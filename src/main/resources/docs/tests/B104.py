##Patterns: B104

import socket

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
##Warn: B104
s.bind(('0.0.0.0', 31137))
s.bind(('192.168.0.1', 8080))
