import os
import sys
import ast
current = sys.version_info[0]
other = 2 if current == 3 else 3
def _classify_file(path):
    try:
        with open(path, 'r') as stream:
            try:
                ast.parse(stream.read())
            except (ValueError, TypeError, UnicodeError):
                # Assume it's the current interpreter.
                return current
            except SyntaxError:
                # the other version or an actual syntax error on current interpreter
                return other
            else:
                return current
    except Exception:
        # Shouldn't happen, but if it does, just assume there's
        # something inherently wrong with the file.
        return current
def classify_file(path):
    interpreter = _classify_file(path)
    return path + "###" + str(interpreter)
def flatten_files(folder):
    for path, _, files in os.walk(folder):
        for file in files:
            if file.endswith(".py"):
                yield os.path.join(path, file)
def walk_items(items):
    for item in items:
        if os.path.isfile(item): yield item
        elif os.path.isdir(item):
            for file in flatten_files(item): yield file
def classify(items):
    for file in walk_items(items):
        print(classify_file(file))
items = filter(None, sys.argv[1:])
classify(items)