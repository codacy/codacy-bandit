##Patterns: B704

from markupsafe import Markup

content = "<script>alert('Hello, world!')</script>"
##Warn: B704
Markup(f"unsafe {content}")
##Warn: B704
Markup(content)

# Safe usage should not be reported
Markup("safe {}").format(content)
