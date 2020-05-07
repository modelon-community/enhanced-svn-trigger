#    Copyright (C) 2020 Modelon AB
#
#    This program is free software: you can redistribute it and/or modify
#    it under the terms of the GNU Lesser General Public License as published by
#    the Free Software Foundation, either version 3 of the License, or
#    (at your option) any later version.
#
#    This program is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU Lesser General Public License for more details.
#
#    You should have received a copy of the GNU Lesser General Public License
#    along with this program.  If not, see <https://www.gnu.org/licenses/>.
#    Copyright (C) 2020 Modelon AB
#
#    This program is free software: you can redistribute it and/or modify
#    it under the terms of the GNU Lesser General Public License as published by
#    the Free Software Foundation, either version 3 of the License, or
#    (at your option) any later version.
#
#    This program is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU Lesser General Public License for more details.
#
#    You should have received a copy of the GNU Lesser General Public License
#    along with this program.  If not, see <https://www.gnu.org/licenses/>.
#!/bin/bash
set -euo pipefail
usage() {
echo '
USAGE
  add-gpl-notice.sh <FILE> <TEMPLATE>
  where FILE is a file that contains a newline-separated list of files. For each file 
  in this list, the contents of the file TEMPLATE is appended at the top of it.
EXAMPLE
  add-gpl-notice.sh java-source-files lgpl-notice-template.java
'
}
trap usage EXIT

file=$1
template=$2

trap - EXIT

for f in `cat $file`
do
    # note that the linebreak in the first -c command is intentional to insert a linebreak before the first line in the file
    # this command opens the file $f in ex(1). each -c command does the following: 
    # 1. inserts a blank line at the top to make room for reading in the file
    # 2. reads in the contents of lgpl-notice-template.java
    # 3. removes the blank line at the top of the file
    # 4. saves the file
    ex \
        -c '1i
    ' \
        -c "1r $template" \
        -c '1d' \
        -c 'x'\
        $f
done
