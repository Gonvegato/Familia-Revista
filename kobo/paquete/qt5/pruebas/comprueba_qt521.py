# Port de QElfParser::parse + findPatternUnloaded de Qt 5.2.1 (32 bits, little endian) para comprobar un .so
import struct,sys
def check(fn):
    d=open(fn,'rb').read(); n=len(d)
    assert n>=64 and d[:4]==b'\x7fELF' and d[4]==1 and d[5]==1
    e_shoff,=struct.unpack_from('<I',d,32); e_shsize,=struct.unpack_from('<H',d,40)  # (Qt lee e_ehsize como "e_shsize")
    assert e_shsize<=n
    e_shentsize,e_shnum,e_shtrndx=struct.unpack_from('<HHH',d,46)
    assert e_shentsize%4==0 and e_shnum*e_shentsize<=n
    soff=e_shoff+e_shentsize*e_shtrndx
    assert soff+e_shentsize<=n and soff%4==0 and soff!=0, 'shstrtab'
    stroff=struct.unpack_from('<10I',d,soff)[4]
    assert stroff+e_shentsize<n and stroff!=0
    res=None
    for i in range(e_shnum):
        name,typ,flags,addr,off,size=struct.unpack_from('<6I',d,e_shoff+i*e_shentsize)
        if name==0: continue
        nm=d[stroff+name:d.index(b'\0',stroff+name)].decode()
        if nm in ('.qtmetadata','.rodata'):
            if not typ&1:
                assert nm!='.rodata','empty .rodata'; continue
            assert off and off+size<=n and size>=1
            if nm=='.qtmetadata': res=(off,size-1); break
    assert res,'no .qtmetadata'
    off,ln=res; rel=d[off:off+ln].find(b'QTMETADATA  '); assert rel>=0
    raw=d[off+rel+12:]; assert raw[:4]==b'qbjs' and struct.unpack_from('<I',raw,4)[0]==1
    size=struct.unpack_from('<I',raw,8)[0]
    print(f'{fn}: OK para Qt 5.2.1 (.qtmetadata en 0x{off:x}, qbjs de {size+8} bytes)')
for f in sys.argv[1:]: check(f)
