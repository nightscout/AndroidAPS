#!/usr/bin/env bash

# This script allows you to run autotune separately for each day of the week

source $(dirname $0)/oref0-bash-common-functions.sh || (echo "ERROR: Failed to run oref0-bash-common-functions.sh. Is oref0 correctly installed?"; exit 1)

usage "$@" <<EOF
Usage: $0 ~/myopenaps http://mynightscouthost.herokuapp.com
If you have day-of-the-week autotune profiles named like
"myopenaps/autotune/profile-day0.json", copies the profile for whichever day
today is to myopenaps/autotune/profile.json and reruns autotune. Day 0 is
Sunday, day 1 is Monday, etc.

This script is not used by a default install.
EOF

[ -z "$OPENAPS_DIR" ] && OPENAPS_DIR="$1"
myopenaps="$OPENAPS_DIR"
nsurl=$2
DOW=$(date +%u)

if [ -z $myopenaps ] || [ -z $nsurl ]; then
  print_usage
  exit
fi

# if we have a day of week profile file, copy that over before autotuning
if [ -e $myopenaps/autotune/profile-day$DOW.json ]; then
  cp $myopenaps/autotune/profile-day$DOW.json $myopenaps/autotune/profile.json
fi

# do the regular autotune and update autotune.json, and then 
# copy the updated profile.json back to the day of week profile file
oref0-autotune -d=$myopenaps -n=$nsurl \
&& cat $myopenaps/autotune/profile.json | json | grep -q start \
&& cp $myopenaps/autotune/profile.json $myopenaps/settings/autotune.json \
&& cp $myopenaps/autotune/profile.json $myopenaps/autotune/profile-day$DOW.json
