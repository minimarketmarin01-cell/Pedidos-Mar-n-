# Lee con pyzbar los bytes ESC/POS y TSPL que genera PruebaBarras.java (como haría un lector).
# Uso: python3 decodificar.py out [png]
import sys, glob, os, re
from PIL import Image
from pyzbar import pyzbar

def img_de_bits(data, wb, h):
    w = wb * 8
    im = Image.new('L', (w, h), 255)
    px = im.load()
    for y in range(h):
        for xb in range(wb):
            b = data[y * wb + xb]
            for k in range(8):
                if b & (0x80 >> k): px[xb * 8 + k, y] = 0
    return im

def de_escpos(raw):
    assert raw[0:2] == b'\x1b\x40' and raw[2:6] == b'\x1d\x76\x30\x00', 'cabecera ESC/POS'
    wb = raw[6] | raw[7] << 8; h = raw[8] | raw[9] << 8
    data = raw[10:10 + wb * h]
    assert len(data) == wb * h
    return img_de_bits(data, wb, h), raw[10 + wb * h:]

def de_tspl(raw):
    m = re.search(rb'BITMAP 0,0,(\d+),(\d+),0,', raw)
    wb, h = int(m.group(1)), int(m.group(2))
    ini = m.end()
    data = raw[ini:ini + wb * h]
    cola = raw[ini + wb * h:]
    return img_de_bits(data, wb, h), raw[:m.start()] + b'...' + cola

ok = True
archivos = 0
for f in sorted(glob.glob(sys.argv[1] + '/*.escpos')) + sorted(glob.glob(sys.argv[1] + '/*.tspl')):
    esperado = os.path.basename(f).rsplit('.', 1)[0].replace('C39_', '')
    archivos += 1
    raw = open(f, 'rb').read()
    im, extra = de_escpos(raw) if f.endswith('.escpos') else de_tspl(raw)
    # agregar margen blanco y escalar x2 sin suavizado (como un lector que ve la etiqueta)
    big = Image.new('L', (im.width + 40, im.height + 40), 255); big.paste(im, (20, 20))
    big = big.resize((big.width * 2, big.height * 2), Image.NEAREST)
    res = pyzbar.decode(big)
    leidos = [(r.type, r.data.decode()) for r in res]
    bien = any(d == esperado for _, d in leidos)
    ok &= bien
    print(('OK ' if bien else 'FALLA'), os.path.basename(f), leidos, 'cola=', extra[-24:])
    if len(sys.argv) > 2: big.save(f + '.png')
ok = ok and archivos > 0
print(('TODO OK' if ok else 'HAY FALLAS') + ' (' + str(archivos) + ' etiquetas)')
sys.exit(0 if ok else 1)
