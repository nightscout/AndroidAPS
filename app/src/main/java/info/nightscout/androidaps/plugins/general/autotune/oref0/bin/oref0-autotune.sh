#!/usr/bin/env bash

# This script sets up an easy test environment for autotune, allowing the user to vary parameters 
# like start/end date.
# 
# Required Inputs: 
#   DIR, (--dir=<OpenAPS Directory>)
#   NIGHTSCOUT_HOST, (--ns-host=<NIGHTSCOUT SITE URL)
#   START_DATE, (--start-date=<YYYY-MM-DD>)
# Optional Inputs:
#   END_DATE, (--end-date=<YYYY-MM-DD>) 
#     if no end date supplied, assume we want a months worth or until day before current day
#   EXPORT_EXCEL (--xlsx=<filenameofexcel>)
#     export to excel. Disabled by default
#   TERMINAL_LOGGING (--log <true/false(true)>
#     logs terminal output to autotune.<date stamp>.log in the autotune directory, default to true
#
# Released under MIT license. See the accompanying LICENSE.txt file for
# full terms and conditions
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.

source $(dirname $0)/oref0-bash-common-functions.sh || (echo "ERROR: Failed to run oref0-bash-common-functions.sh. Is oref0 correctly installed?"; exit 1)

die() {
    if [[ -z "$API_SECRET" ]]; then
        echo "Warning: API_SECRET is not set when calling oref0-autotune.sh"
        echo "(this is only a problem if you have locked down read-only access to your NS)."
    fi

    echo "$@"
    exit 1
}

# defaults
CURL_FLAGS="--compressed"
DIR=""
NIGHTSCOUT_HOST=""
START_DATE=""
END_DATE=""
START_DAYS_AGO=1  # Default to yesterday if not otherwise specified
END_DAYS_AGO=1  # Default to yesterday if not otherwise specified
EXPORT_EXCEL="" # Default is to not export to Microsoft Excel
TERMINAL_LOGGING=true
CATEGORIZE_UAM_AS_BASAL=false
TUNE_INSULIN_CURVE=false
RECOMMENDS_REPORT=true
UNKNOWN_OPTION=""

if [ -n "${API_SECRET_READ}" ]; then 
   echo "WARNING: API_SECRET_READ is deprecated starting with oref 0.6.x. The Nightscout authentication information is now used from the API_SECRET environment variable"
fi

# If we are running OS X or BSD, we need to use a different version
# of the 'date' command; the built-in 'date' is BSD, which
# has fewer options than the linux version.  So the user
# needs to install coreutils, which gives the GNU 'date'
# command as 'gdate':

shopt -s expand_aliases

if [[ `uname` == 'Darwin' || `uname` == 'FreeBSD' || `uname` == 'OpenBSD' ]] ; then
    alias date='gdate'
fi

# handle input arguments
for i in "$@"
do
case $i in
    -d=*|--dir=*)
    DIR="${i#*=}"
    # ~/ paths have to be expanded manually
    DIR="${DIR/#\~/$HOME}"
    # If DIR is a symlink, get actual path: 
    if [[ -L $DIR ]] ; then
        directory="$(readlink $DIR)"
    else
        directory="$DIR"
    fi
    shift # past argument=value
    ;;
    -n=*|--ns-host=*)
    NIGHTSCOUT_HOST="${i#*=}"
    shift # past argument=value
    ;;
    -s=*|--start-date=*)
    START_DATE="${i#*=}"
    START_DATE=`date --date="$START_DATE" +%Y-%m-%d`
    shift # past argument=value
    ;;
    -e=*|--end-date=*)
    END_DATE="${i#*=}"
    END_DATE=`date --date="$END_DATE" +%Y-%m-%d`
    shift # past argument=value
    ;;
    -t=*|--start-days-ago=*)
    START_DAYS_AGO="${i#*=}"
    shift # past argument=value
    ;;
    -d=*|--end-days-ago=*)
    END_DAYS_AGO="${i#*=}"
    shift # past argument=value
    ;;
    -x=*|--xlsx=*)
    EXPORT_EXCEL="${i#*=}"
    shift # past argument=value
    ;;
    -l=*|--log=*)
    TERMINAL_LOGGING="${i#*=}"
    shift
    ;;
    -c=*|--categorize-uam-as-basal=*)
    CATEGORIZE_UAM_AS_BASAL="${i#*=}"
    shift
    ;;
    -i=*|--tune-insulin-curve=*)
    TUNE_INSULIN_CURVE="${i#*=}"
    shift
    ;;
    *)
    # unknown option
    echo "Option ${i#*=} unknown"
    UNKNOWN_OPTION="yes"
    ;;
esac
done

# remove any trailing / from NIGHTSCOUT_HOST
NIGHTSCOUT_HOST=$(echo $NIGHTSCOUT_HOST | sed 's/\/$//g')

if [[ -z "$DIR" || -z "$NIGHTSCOUT_HOST" ]]; then
    echo "Usage: oref0-autotune <--dir=myopenaps_directory> <--ns-host=https://mynightscout.azurewebsites.net> [--start-days-ago=number_of_days] [--end-days-ago=number_of_days] [--start-date=YYYY-MM-DD] [--end-date=YYYY-MM-DD] [--xlsx=autotune.xlsx] [--log=(true)|false] [--categorize-uam-as-basal=true|(false)] [--tune-insulin-curve=true|(false) ]"
exit 1
fi
if [[ -z "$START_DATE" ]]; then
    # Default start date of yesterday
    START_DATE=`date --date="$START_DAYS_AGO days ago" +%Y-%m-%d`
fi
if [[ -z "$END_DATE" ]]; then
    # Default end-date as this morning at midnight in order to not get partial day samples for now
    # (ISF/CSF adjustments are still single values across each day)
    END_DATE=`date --date="$END_DAYS_AGO days ago" +%Y-%m-%d`
fi

if [[ -z "$UNKNOWN_OPTION" ]] ; then # everything is ok
  echo "Running oref0-autotune --dir=$DIR --ns-host=$NIGHTSCOUT_HOST --start-date=$START_DATE --end-date=$END_DATE --categorize-uam-as-basal=$CATEGORIZE_UAM_AS_BASAL"
else
  echo "Unknown options. Exiting"
  exit 1
fi

# Get profile for testing copied to home directory.
cd $directory && mkdir -p autotune
cp settings/pumpprofile.json autotune/profile.pump.json || die "Cannot copy settings/pumpprofile.json"
# This allows manual users to be able to run autotune by simply creating a settings/pumpprofile.json file.
if [[ `uname` == 'Darwin' || `uname` == 'FreeBSD' || `uname` == 'OpenBSD' ]] ; then
    cp settings/pumpprofile.json settings/profile.json || die "Cannot copy settings/pumpprofile.json"
else
    cp -up settings/pumpprofile.json settings/profile.json || die "Cannot copy settings/pumpprofile.json"
fi
# If a previous valid settings/autotune.json exists, use that; otherwise start from settings/profile.json
cp settings/autotune.json autotune/profile.json && cat autotune/profile.json | jq . | grep -q start || cp autotune/profile.pump.json autotune/profile.json
cd autotune

# Turn on stderr logging, if enabled (default to true)
if [[ $TERMINAL_LOGGING = "true" ]]; then
  # send stderr to a file as well as the terminal
  exec &> >(tee -a autotune.$(date +%Y-%m-%d-%H%M%S).log)
fi

# Build date list for autotune iteration
date_list=()
date=$START_DATE; 
while :
do 
  date_list+=( "$date" )
  if [ $date != "$END_DATE" ]; then 
    date="$(date --date="$date + 1 days" +%Y-%m-%d)"; 
  else 
    break
  fi
done

echo "Compressing old json and log files to save space..."
gzip -f ns-*.json
gzip -f autotune*.json
# only gzip autotune log files more than 2 days old
find autotune.*.log -mtime +2 | while read file; do gzip -f $file; done
echo "Autotune disk usage:"
du -h .
echo "Overall disk used/avail:"
df -h .

echo "Grabbing NIGHTSCOUT treatments.json and entries/sgv.json for date range..."

# Get Nightscout BG (sgv.json) Entries
for i in "${date_list[@]}"
do 
    # pull CGM data from 4am-4am
    query="find%5Bdate%5D%5B%24gte%5D=$(to_epochtime "$i +4 hours" |nonl; echo 000)&find%5Bdate%5D%5B%24lte%5D=$(to_epochtime "$i +28 hours" |nonl; echo 000)&count=1500"
    echo Query: $NIGHTSCOUT_HOST entries/sgv.json $query
    ns-get host $NIGHTSCOUT_HOST entries/sgv.json $query > ns-entries.$i.json || die "Couldn't download ns-entries.$i.json"
    ls -la ns-entries.$i.json || die "No ns-entries.$i.json downloaded"

    # Get Nightscout carb and insulin Treatments
    # echo $i $START_DATE;
    #query="find%5Bdate%5D%5B%24gte%5D=$(to_epochtime $i |nonl; echo 000)&find%5Bdate%5D%5B%24lte%5D=$(to_epochtime "$i +1 days" |nonl; echo 000)&count=1000"
    # to capture UTC-dated treatments, we need to capture an extra 12h on either side, plus the DIA lookback
    # 18h = 12h for timezones + 6h for DIA; 40h = 28h for 4am + 12h for timezones
    query="find%5Bcreated_at%5D%5B%24gte%5D=`date --date="$i -18 hours" -Iminutes`&find%5Bcreated_at%5D%5B%24lte%5D=`date --date="$i +42 hours" -Iminutes`"
    echo Query: $NIGHTSCOUT_HOST treatments.json $query
    ns-get host $NIGHTSCOUT_HOST treatments.json $query > ns-treatments.$i.json || die "Couldn't download ns-treatments.$i.json"
    ls -la ns-treatments.$i.json || die "No ns-treatments.$i.json downloaded"


    # Do iterative runs over date range, save autotune.json (prepped data) and input/output profile.json
    cp profile.json profile.$i.json
    # Autotune Prep (required args, <pumphistory.json> <profile.json> <glucose.json>), output prepped glucose 
    # data or <autotune/glucose.json> below
    if [[ $CATEGORIZE_UAM_AS_BASAL = "true" ]]; then
        CATEGORIZE_UAM_AS_BASAL_OPT="--categorize_uam_as_basal"
    else
        CATEGORIZE_UAM_AS_BASAL_OPT=
    fi

    if [[ $TUNE_INSULIN_CURVE = "true" ]]; then
        TUNE_INSULIN_CURVE_OPT="--tune-insulin-curve"
    else
        TUNE_INSULIN_CURVE_OPT=
    fi

    echo "oref0-autotune-prep $CATEGORIZE_UAM_AS_BASAL_OPT $TUNE_INSULIN_CURVE_OPT ns-treatments.$i.json profile.json ns-entries.$i.json profile.pump.json > autotune.$i.json"
    oref0-autotune-prep $CATEGORIZE_UAM_AS_BASAL_OPT $TUNE_INSULIN_CURVE_OPT ns-treatments.$i.json profile.json ns-entries.$i.json profile.pump.json > autotune.$i.json \
        || die "Could not run oref0-autotune-prep ns-treatments.$i.json profile.json ns-entries.$i.json"
    
    # Autotune  (required args, <autotune/glucose.json> <autotune/autotune.json> <settings/profile.json>), 
    # output autotuned profile or what will be used as <autotune/autotune.json> in the next iteration
    echo "oref0-autotune-core autotune.$i.json profile.json profile.pump.json > newprofile.$i.json"
    if ! oref0-autotune-core autotune.$i.json profile.json profile.pump.json > newprofile.$i.json; then
        if cat profile.json | jq --exit-status .carb_ratio==null; then
            echo "ERROR: profile.json contains null carb_ratio: using profile.pump.json"
            cp profile.pump.json profile.json
            exit
        else
            die "Could not run oref0-autotune-core autotune.$i.json profile.json profile.pump.json"
        fi
    else
        # Copy tuned profile produced by autotune to profile.json for use with next day of data
        if cat newprofile.$i.json | jq . | grep -q start; then
            cp newprofile.$i.json profile.json
        else
            jq -c newprofile.$i.json
            die "newprofile.$i.json invalid"
        fi
    fi


    if ! [[ -z "$EXPORT_EXCEL" ]]; then
        echo Exporting to $EXPORT_EXCEL
        oref0-autotune-export-to-xlsx --dir $DIR --output $EXPORT_EXCEL
    fi

    # Create Summary Report of Autotune Recommendations and display in the terminal
    if [[ $RECOMMENDS_REPORT == "true" ]]; then
        # Set the report file name, so we can let the user know where it is and cat
        # it to the screen
        report_file=$directory/autotune/autotune_recommendations.log

        echo
        echo "Autotune pump profile recommendations:"
        echo "---------------------------------------------------------"

        # Let the user know where the Autotune Recommendations are logged
        echo "Recommendations Log File: $report_file"
        echo

        # Run the Autotune Recommends Report
        oref0-autotune-recommends-report $directory

        # Go ahead and echo autotune_recommendations.log to the terminal, minus blank lines
        cat $report_file | egrep -v "\| *\| *$"
    fi

done # End Date Range Iteration
