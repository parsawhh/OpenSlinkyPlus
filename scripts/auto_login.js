// @slinky {"id":"auto_login","name":"AutoLogin","description":"Myau AutoLogin - faithful behavior port","category":"utility",
// "settings":[
//   {"key":"auto_register","label":"Auto Register","type":"boolean","default":true},
//   {"key":"auto_login","label":"Auto Login","type":"boolean","default":true},
//   {"key":"password","label":"Password","type":"choice","default":0,"options":["axolotlaxolotl","12341234","password","changeme"]},
//   {"key":"min_delay","label":"Min Delay","type":"number","default":150,"min":0,"max":1000,"step":10},
//   {"key":"max_delay","label":"Max Delay","type":"number","default":300,"min":0,"max":1000,"step":10}
// ],
// "listens":["tick","packet_inbound"]}

var REGISTER_PROMPT = /(^|\s)\/(?:register|reg)(\s|$)/;
var LOGIN_PROMPT = /(^|\s)\/(?:login|log|l)(\s|$)/;
var Status = {WAITING:0, QUEUED:1, SENT_COMMAND:2, STOPPED:3};
var Action = {NONE:0, REGISTER:1, LOGIN:2};

var status = Status.WAITING;
var queuedAction = Action.NONE;
var lastAction = Action.NONE;
var queuedAt = -1;
var passwords = ["axolotlaxolotl","12341234","password","changeme"];

function onEnable(api){ resetState(); }
function onDisable(api){ resetState(); }

function getPassword(api){ var i=api.num('password')|0; if(i<0||i>=passwords.length) i=0; return passwords[i]; }
function clampDelay(api){ var mn=api.num('min_delay')|0, mx=api.num('max_delay')|0; if(mx<mn){var t=mn;mn=mx;mx=t;} return [mn,mx]; }
function nextLong(min,max){ return min + Math.floor(Math.random()*((max-min)+1)); }

function normalize(msg){ if(msg==null) return ''; return String(msg).toLowerCase().replace(/\n/g,' ').replace(/\s+/g,' ').trim(); }

function isSuccessMessage(message){ return message.indexOf('registered')>=0||message.indexOf('register successful')>=0||message.indexOf('login successful')>=0||message.indexOf('logged in')>=0||message.indexOf('successfully logged')>=0||message.indexOf('success')>=0; }
function isFailureMessage(message){ return message.indexOf('incorrect')>=0||message.indexOf('wrong')>=0||message.indexOf('invalid')>=0||message.indexOf('failed')>=0||message.indexOf('error')>=0||message.indexOf('try again')>=0; }
function isStopMessage(message){ return message.indexOf('already logged')>=0||message.indexOf('already registered')>=0||message.indexOf('unknown command')>=0||message.indexOf('you are already')>=0; }

function queue(api, action){
  if(action===Action.NONE) return;
  var p=api.player(); if(!p||!p.inWorld) return;
  var d=clampDelay(api); var delay=nextLong(d[0],d[1]);
  queuedAction=action; queuedAt=api.now()+delay; status=Status.QUEUED;
  api.log('Queueing '+(action===Action.REGISTER?'register':'login')+' in '+delay+'ms');
}

function handlePrompt(api, message){
  if(api.bool('auto_register') && REGISTER_PROMPT.test(message)){ queue(api, Action.REGISTER); return true; }
  if(api.bool('auto_login') && LOGIN_PROMPT.test(message)){ queue(api, Action.LOGIN); return true; }
  return false;
}

function readMessage(api, event){
  var cls=event.packetClass()||'';
  var msg='';
  try{
    var ref=event.packetRef(); if(!ref) return '';
    if(cls.indexOf('S02PacketChat')>=0 || cls.indexOf('Chat')>=0){
      var comp=ref.call('getChatComponent') || ref.call('func_148915_c');
      if(comp){ var c2=api.ref(comp); msg = c2.call('getUnformattedText') || c2.call('func_150260_c') || ''; }
      if(!msg) msg = ref.call('getFormattedText') || '';
    } else if(cls.indexOf('S45PacketTitle')>=0 || cls.indexOf('Title')>=0){
      var m=ref.call('getMessage'); if(m){ var c2=api.ref(m); msg=c2.call('getUnformattedText')||''; }
    } else if(cls.indexOf('S40PacketDisconnect')>=0){ return '__DISCONNECT__'; }
  }catch(e){}
  return normalize(msg);
}

function resetState(){ status=Status.WAITING; queuedAction=Action.NONE; lastAction=Action.NONE; queuedAt=-1; }

function onEvent(api,event){
  if(!api.isEnabled || !api.isEnabled()) return;
  var t=event.getType();
  if(t===2){ // inbound packet
    var message = readMessage(api,event);
    if(message==='__DISCONNECT__'){ resetState(); return; }
    if(!message) return;
    if(status===Status.WAITING){ handlePrompt(api,message); }
    else if(status===Status.QUEUED || status===Status.SENT_COMMAND){
      if(isSuccessMessage(message)){
        api.log((lastAction===Action.REGISTER?'Register':'Login')+' successful.');
        status=Status.STOPPED; queuedAction=Action.NONE; queuedAt=-1;
      } else if(isFailureMessage(message)){
        api.log((lastAction===Action.REGISTER?'Register':'Login')+' failed. Check your password.');
        status=Status.STOPPED; queuedAction=Action.NONE; queuedAt=-1;
      } else if(isStopMessage(message)){
        status=Status.STOPPED; queuedAction=Action.NONE; queuedAt=-1;
      }
    }
    return;
  }
  if(t===0){ // tick
    if(queuedAction===Action.NONE || queuedAt<0) return;
    var p=api.player(); if(!p||!p.inWorld) return;
    if(api.now() < queuedAt) return;
    var password=getPassword(api);
    if(!password || password.trim()==='' || password.indexOf(' ')>=0){ api.log('Password invalid'); status=Status.STOPPED; queuedAction=Action.NONE; queuedAt=-1; return; }
    var cmd = queuedAction===Action.REGISTER ? ('/register '+password+' '+password) : ('/login '+password);
    lastAction=queuedAction; queuedAction=Action.NONE; queuedAt=-1; status=Status.SENT_COMMAND;
    if(api.say) api.say(cmd); else if(api.message) api.message(cmd);
  }
}
