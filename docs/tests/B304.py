##Patterns: B304

from Crypto.Cipher import ARC2
from Crypto.Cipher import ARC4
from Crypto.Cipher import Blowfish
from Crypto.Cipher import DES
from Crypto.Cipher import XOR
from Crypto.Hash import SHA
from Crypto import Random
from Crypto.Util import Counter
from cryptography.hazmat.primitives.ciphers import Cipher
from cryptography.hazmat.primitives.ciphers import algorithms
from cryptography.hazmat.primitives.ciphers import modes
from cryptography.hazmat.backends import default_backend
from struct import pack
import socket

key = b'Sixteen byte key'
iv = Random.new().read(ARC2.block_size)
##Warn: B304
cipher = ARC2.new(key, ARC2.MODE_CFB, iv)
msg = iv + cipher.encrypt(b'Attack at dawn')

key = b'Very long and confidential key'
nonce = Random.new().read(16)
tempkey = SHA.new(key+nonce).digest()
##Warn: B304
cipher = ARC4.new(tempkey)
msg = nonce + cipher.encrypt(b'Open the pod bay doors, HAL')

bs = Blowfish.block_size
key = b'An arbitrarily long key'
iv = Random.new().read(bs)
##Warn: B304
cipher = Blowfish.new(key, Blowfish.MODE_CBC, iv)
plaintext = b'docendo discimus '
plen = bs - divmod(len(plaintext),bs)[1]
padding = [plen]*plen
padding = pack('b'*plen, *padding)
msg = iv + cipher.encrypt(plaintext + padding)

key = b'-8B key-'
nonce = Random.new().read(DES.block_size/2)
ctr = Counter.new(DES.block_size*8/2, prefix=nonce)
##Warn: B304
cipher = DES.new(key, DES.MODE_CTR, counter=ctr)
plaintext = b'We are no longer the knights who say ni!'
msg = nonce + cipher.encrypt(plaintext)

key = b'Super secret key'
##Warn: B304
cipher = XOR.new(key)
plaintext = b'Encrypt me'
msg = cipher.encrypt(plaintext)

##Warn: B304
cipher = Cipher(algorithms.ARC4(key), mode=None, backend=default_backend())
encryptor = cipher.encryptor()
ct = encryptor.update(b"a secret message")

##Warn: B304
cipher = Cipher(algorithms.Blowfish(key), mode=None, backend=default_backend())
encryptor = cipher.encryptor()
ct = encryptor.update(b"a secret message")

##Warn: B304
cipher = Cipher(algorithms.IDEA(key), mode=None, backend=default_backend())
encryptor = cipher.encryptor()
ct = encryptor.update(b"a secret message")

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.bind(('0.0.0.0', 31137))
s.bind(('192.168.0.1', 8080))
