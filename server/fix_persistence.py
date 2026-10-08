import re

with open('internal/server/persistence.go', 'r') as f:
    lines = f.readlines()

new_lines = []
in_import = False
import_count = 0

for line in lines:
    if line.startswith('import ('):
        import_count += 1
        if import_count > 1:
            in_import = True
            continue
    if in_import and line.strip() == ')':
        in_import = False
        continue
    if not in_import:
        new_lines.append(line)

with open('internal/server/persistence.go', 'w') as f:
    f.writelines(new_lines)
