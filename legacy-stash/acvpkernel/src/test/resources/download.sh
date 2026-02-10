#!/usr/bin/env bash

# https://boringssl.googlesource.com/boringssl/+archive/refs/heads/master/util/fipstools/acvp/acvptool/test/expected.tar.gz
# https://boringssl.googlesource.com/boringssl/+archive/refs/heads/master/util/fipstools/acvp/acvptool/test/vectors.tar.gz
# relative path
$(dirname $0)
# full path
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
### prepare a result directory to the current dir
ex_dir="$DIR/expected"
[ ! -d "$ex_dir" ] && mkdir -p "$ex_dir"
vec_dir="$DIR/vector"
[ ! -d "$vec_dir" ] && mkdir -p "$vec_dir"
#sync data from boringssl website
BORING_URL="https://boringssl.googlesource.com/boringssl/+archive/refs/heads/master/util/fipstools/acvp/acvptool/test"
cd $ex_dir
curl "$BORING_URL/expected.tar.gz" --output expected.tar.gz
echo $ex_dir/expected.tar.gz
if [ ! -d "$ex_dir/expected.tar.gz" ]; then
  echo "here"
  tar -zxvf expected.tar.gz
fi
cd $vec_dir
curl "$BORING_URL/vectors.tar.gz" --output vectors.tar.gz
if [ ! -d "$vec_dir/vectors.tar.gz" ]; then
  tar -zxvf vectors.tar.gz
fi

#cd vec_dir
#wget "https://boringssl.googlesource.com/boringssl/+archive/refs/heads/master/util/fipstools/acvp/acvptool/test/vectors.tar.gz"

#exit 0