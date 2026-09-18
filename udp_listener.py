import socket
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(('0.0.0.0', 8888))
print('listening on 8888...')
while True:
    d, a = s.recvfrom(64)
    if len(d) >= 4 and d[0] == 0xA5:
        print(f'from {a[0]}: L={d[1]} R={d[2]} checksum_ok={d[3] == (0xA5 ^ d[1] ^ d[2])}')
    else:
        print('raw:', d.hex())
