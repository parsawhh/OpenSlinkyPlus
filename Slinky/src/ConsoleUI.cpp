#include "ConsoleUI.hpp"
#include <cstdio>
#include <cstring>
#include <cmath>

namespace Slinky {

CRITICAL_SECTION ConsoleUI::s_CriticalSection;
char ConsoleUI::s_StatusText[512] = { 0 };
std::atomic<bool> ConsoleUI::s_Running(false);
HANDLE ConsoleUI::s_hAnimationThread = nullptr;
DWORD ConsoleUI::s_OriginalConsoleMode = 0;
HANDLE ConsoleUI::s_hConsole = INVALID_HANDLE_VALUE;

static void GetAstolfoColor(float offset, int& outR, int& outG, int& outB) {
    double hue = fmod((double)offset, 1.0);
    if (hue < 0.0) hue += 1.0;

    double stage = hue * 2.0;
    if (stage > 1.0) stage = 2.0 - stage;

    double finalHue = 0.48 + stage * 0.37;

    float s = 0.60f;
    float v = 1.0f;

    float h = (float)(finalHue * 6.0);
    int i = (int)floor(h);
    float f = h - (float)i;
    float p = v * (1.0f - s);
    float q = v * (1.0f - s * f);
    float t = v * (1.0f - s * (1.0f - f));

    float r = 0, g = 0, b = 0;
    switch (i % 6) {
        case 0: r = v; g = t; b = p; break;
        case 1: r = q; g = v; b = p; break;
        case 2: r = p; g = v; b = t; break;
        case 3: r = p; g = q; b = v; break;
        case 4: r = t; g = p; b = v; break;
        case 5: r = v; g = p; b = q; break;
    }

    outR = (int)(r * 255.0f);
    outG = (int)(g * 255.0f);
    outB = (int)(b * 255.0f);
}

void ConsoleUI::PrintBanner() {
    static const char* const art[] = {
        "                            @*  *",
        "                   __o=#*^^`#^^**=o__",
        "                _<*`                `*%_",
        "             _@*       /`       \\       *%_",
        "            @^   }    #          %    \\   ^%",
        "          _#    #    #            *    %    *_",
        "         ,^                                  ^@",
        "        _%                                    ^_",
        "        # _,_   _                      _   _,_ {",
        "        `{   @@O@@,,,,_,,,,@@,,,,_,,,,@@@@@   } >",
        "       @@^*=*%}*#^@  # %  _%^_  # %_ _^*#{^@=*^%}",
        "      _*    @ } %_#@###%%_%  ^@@=###@@ # { @    *@",
        "     #      ^ ` %{_#\"``^%^* %*^#^``^%_}    ^      %",
        "    _^       @   {# __@_ *_   # __@_ *@   }        @",
        "    #    }   ^_  #  ####  }  {  ####  #  _`   @    #",
        "   @`    `    %  #  ####  }  %  ####  @  *          @",
        "   #    @      }  % *##*_#  , %_*##* # @#      @    %",
        "  @     {      #@    >=#`      `*=<    #@      }     }",
        "  @     #     @%_  %_    <____>    _#` _#_     {     }",
        "   @    *     #  \"%,_^%<___  ___,#\"_,#*  }     ^     `",
        "   }         _%      `^%} ```` @#*`      ^_         {",
        "   {         #                            %         #",
        "   *_       @     ____~@__    __@~____     \\       _*"
    };
    const size_t artLines = sizeof(art) / sizeof(art[0]);

    for (size_t row = 0; row < artLines; ++row) {
        const char* line = art[row];
        for (size_t i = 0; line[i] != '\0'; ++i) {
            if (line[i] == ' ') {
                putchar(' ');
                continue;
            }
            int r = 0, g = 0, b = 0;
            GetAstolfoColor((float)row * 0.03f + (float)i * 0.008f, r, g, b);
            printf("\x1b[38;2;%d;%d;%dm%c", r, g, b, line[i]);
        }
        printf("\x1b[0m\n");
    }
    printf("\x1b[38;2;255;170;215m  OpenSlinky+\x1b[0m by \x1b[38;2;160;215;255mlallu\x1b[0m   |   credits: \x1b[38;2;255;255;255mSkid Solutions\x1b[0m\n");
    fflush(stdout);
}

void ConsoleUI::Initialize() {
    InitializeCriticalSection(&s_CriticalSection);
    s_hConsole = GetStdHandle(STD_OUTPUT_HANDLE);

    if (s_hConsole != INVALID_HANDLE_VALUE) {
        GetConsoleMode(s_hConsole, &s_OriginalConsoleMode);
        SetConsoleMode(s_hConsole, s_OriginalConsoleMode | 0x0004);
    }

    SetConsoleTitleW(L"OpenSlinky+ by lallu");
    strcpy_s(s_StatusText, "SCANNING");

    PrintBanner();
}

void ConsoleUI::Shutdown() {
    StopAnimation();
    printf("\x1b[?25h");
    if (s_hConsole != INVALID_HANDLE_VALUE && s_OriginalConsoleMode != 0) {
        SetConsoleMode(s_hConsole, s_OriginalConsoleMode);
    }
    DeleteCriticalSection(&s_CriticalSection);
}

void ConsoleUI::SetStatusText(const char* text) {
    EnterCriticalSection(&s_CriticalSection);
    strncpy_s(s_StatusText, text, _TRUNCATE);
    LeaveCriticalSection(&s_CriticalSection);
}

void ConsoleUI::PrintError(const char* action, DWORD errorCode) {
    wchar_t* msgBuf = nullptr;
    FormatMessageW(
        FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
        nullptr, errorCode, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
        (LPWSTR)&msgBuf, 0, nullptr);

    char errorStr[512] = "unknown";
    if (msgBuf) {
        WideCharToMultiByte(CP_UTF8, 0, msgBuf, -1, errorStr, 512, nullptr, nullptr);
        LocalFree(msgBuf);

        size_t len = strlen(errorStr);
        while (len > 0 && (errorStr[len - 1] == '\r' || errorStr[len - 1] == '\n')) {
            errorStr[--len] = '\0';
        }
    }

    EnterCriticalSection(&s_CriticalSection);
    printf("\n[!] %s failed with error %lu: %s\n", action, errorCode, errorStr);
    fflush(stdout);
    LeaveCriticalSection(&s_CriticalSection);
}

void ConsoleUI::StartAnimation() {
    if (s_Running) return;
    s_Running = true;
    printf("\x1b[?25l\n\n");
    fflush(stdout);
    s_hAnimationThread = CreateThread(nullptr, 0, AnimationThread, nullptr, 0, nullptr);
}

void ConsoleUI::StopAnimation() {
    if (!s_Running) return;
    s_Running = false;
    if (s_hAnimationThread) {
        WaitForSingleObject(s_hAnimationThread, 1000);
        CloseHandle(s_hAnimationThread);
        s_hAnimationThread = nullptr;
    }
    printf("\x1b[?25h\n");
    fflush(stdout);
}

DWORD WINAPI ConsoleUI::AnimationThread(LPVOID lpParam) {
    const char* spinner[] = { "-", "\\", "|", "/" };
    const char* brandLine = "   OpenSlinky+ by lallu  |  credits: Skid Solutions";
    int frame = 0;

    while (s_Running) {
        char currentStatus[512] = { 0 };
        EnterCriticalSection(&s_CriticalSection);
        strncpy_s(currentStatus, s_StatusText, _TRUNCATE);
        LeaveCriticalSection(&s_CriticalSection);

        printf("\x1b[2A\r\x1b[2K");
        for (size_t i = 0; brandLine[i] != '\0'; ++i) {
            int r = 0, g = 0, b = 0;
            GetAstolfoColor((float)frame * 0.025f - (float)i * 0.035f, r, g, b);
            printf("\x1b[38;2;%d;%d;%dm%c", r, g, b, brandLine[i]);
        }
        printf("\x1b[0m\n\n\r\x1b[2K  [%s] %s", spinner[(frame / 3) % 4], currentStatus);
        fflush(stdout);

        frame++;
        Sleep(50);
    }

    char finalStatus[512] = { 0 };
    EnterCriticalSection(&s_CriticalSection);
    strncpy_s(finalStatus, s_StatusText, _TRUNCATE);
    LeaveCriticalSection(&s_CriticalSection);

    printf("\x1b[2A\r\x1b[2K");
    for (size_t i = 0; brandLine[i] != '\0'; ++i) {
        int r = 0, g = 0, b = 0;
        GetAstolfoColor((float)frame * 0.025f - (float)i * 0.035f, r, g, b);
        printf("\x1b[38;2;%d;%d;%dm%c", r, g, b, brandLine[i]);
    }
    printf("\x1b[0m\n\n\r\x1b[2K  [+] %s\n\x1b[?25h", finalStatus);
    fflush(stdout);

    return 0;
}

} // namespace Slinky
