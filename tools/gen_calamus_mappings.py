#!/usr/bin/env python3
"""
gen_calamus_mappings.py - builds SlinkyClient/mappings/calamus.txt for the Ornithe/OneClient runtime.

OneClient 1.8.9 runs Fabric-loader (Knot) with Ornithe calamus-intermediary-gen2. At runtime the
Minecraft classes keep their MCP class names (net.minecraft.client.Minecraft) but members use
calamus intermediary names (f_33079084 / m_20213497 style). This script composes:

  official.txt (named -> notch)  x  calamus.tiny (official -> intermediary)

into the dev/slinky/inject/mappings.txt format (CL identity omitted, FD/MD named -> intermediary):

  FD  <namedOwner>  <namedField>  <intermediaryField>
  MD  <namedOwner>  <namedMethod> <namedDescriptor> <intermediaryMethod>

usage: python3 gen_calamus_mappings.py <official.txt> <calamus.tiny> <out.txt>
"""
import sys

def parse_official(path):
    cl, fd, md = {}, {}, {}
    for line in open(path, encoding='utf-8'):
        c = line.rstrip('\n').split('\t')
        if c[0] == 'CL':
            cl[c[1]] = c[2]
        elif c[0] == 'FD':
            fd[(c[1], c[2])] = c[3]
        elif c[0] == 'MD':
            md[(c[1], c[2], c[3])] = c[4]
    return cl, fd, md

def parse_calamus(path):
    """tiny v2 (member lines carry a leading tab), namespaces [official, intermediary]."""
    cls, fields, methods = {}, {}, {}
    owner = None
    for line in open(path, encoding='utf-8'):
        line = line.rstrip('\n')
        if not line:
            continue
        leading_tab = line[0] == '\t'
        c = line.lstrip('\t').split('\t')
        if not leading_tab and c[0] == 'c':
            owner = c[1]
            cls[c[1]] = c[2]
        elif leading_tab and c[0] == 'f' and owner:
            fields[(owner, c[2])] = c[3]
        elif leading_tab and c[0] == 'm' and owner:
            methods[(owner, c[2], c[1])] = c[3]
    return cls, fields, methods

def main(official_path, calamus_path, out_path):
    ncl, nfd, nmd = parse_official(official_path)
    ccls, cfd, cmd = parse_calamus(calamus_path)
    # named class -> official (notch) class; identity when official.txt has no entry
    n2o = lambda named: ncl.get(named, named)

    def to_official_descriptor(named_desc):
        # official.txt MD carries named descriptors; calamus.tiny carries notch descriptors.
        out, i = [], 0
        while i < len(named_desc):
            if named_desc[i] == 'L':
                end = named_desc.index(';', i)
                out.append('L' + n2o(named_desc[i + 1:end]) + ';')
                i = end + 1
            else:
                out.append(named_desc[i])
                i += 1
        return ''.join(out)

    out = []
    for (owner, name), official in nfd.items():
        inter = cfd.get((n2o(owner), official))
        if inter is not None:
            out.append('FD\t%s\t%s\t%s' % (owner, name, inter))
    for (owner, name, desc), official in nmd.items():
        inter = cmd.get((n2o(owner), official, to_official_descriptor(desc)))
        if inter is not None:
            out.append('MD\t%s\t%s\t%s\t%s' % (owner, name, desc, inter))
    out.sort()
    open(out_path, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
    print('%s: %d FD + %d MD entries (from %d/%d official + %d/%d calamus)' % (
        out_path, sum(1 for l in out if l.startswith('FD')),
        sum(1 for l in out if l.startswith('MD')),
        len(nfd), len(nmd), len(cfd), len(cmd)))

if __name__ == '__main__':
    main(*sys.argv[1:4])
