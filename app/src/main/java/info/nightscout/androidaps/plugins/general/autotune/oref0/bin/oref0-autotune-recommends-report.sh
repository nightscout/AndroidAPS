#!/usr/bin/env bash

# This script creates a Summary Report of Autotune Recommendations. The report itself is
# structured in such a way intended to give the end user a quick look at what needs to change
# on their pump. Report is stored in the autotune sub-directory within the user's OpenAPS
# directory.
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
#
# Example usage: ~/src/oref0/bin/oref0-autotune-recommends-report.sh <OpenAPS Loop Directory Path>

source $(dirname $0)/oref0-bash-common-functions.sh || (echo "ERROR: Failed to run oref0-bash-common-functions.sh. Is oref0 correctly installed?"; exit 1)


# fix problems with locales with printf output
LC_NUMERIC=en_US.UTF-8

# Use alternate date command if on OS X:
shopt -s expand_aliases

if [[ `uname` == 'Darwin' || `uname` == 'FreeBSD' || `uname` == 'OpenBSD' ]] ; then
    alias date='gdate'
fi

usage "$@" <<EOT
Usage: ./oref0-autotune-recommends-report.sh <OpenAPS Loop Directory Path>
Create a summary report of Autotune recommendations, and store it in
<loop directory>/autotune/autotune_recommendations.log
EOT

if [ $# -ne 1 ]; then
    print_usage
    exit 1
fi

# OpenAPS Directory Input
directory=$1

# Set report filename and delete the old one if it exists
report_file=$directory/autotune/autotune_recommendations.log
[ -f $report_file ] && rm $report_file

# Report Column Widths
parameter_width=15
data_width=12

# Get current profile info
basal_minutes_current=( $(jq -r '.basalprofile[].minutes' $directory/autotune/profile.pump.json) )
basal_rate_current=( $(jq -r '.basalprofile[].rate' $directory/autotune/profile.pump.json) )
isf_current=$(cat $directory/autotune/profile.pump.json | jq '.isfProfile.sensitivities[0].sensitivity')
csf_current=$(cat $directory/autotune/profile.pump.json | jq '.csf')
carb_ratio_current=$(cat $directory/autotune/profile.pump.json | jq '.carb_ratio')

# Get autotune profile info
basal_minutes_new=( $(jq -r '.basalprofile[].minutes' $directory/autotune/profile.json) )
basal_rate_new=( $(jq -r '.basalprofile[].rate' $directory/autotune/profile.json) )
basal_untuned_new=( $(jq -r '.basalprofile[].untuned' $directory/autotune/profile.json) )
isf_new=$(cat $directory/autotune/profile.json | jq '.isfProfile.sensitivities[0].sensitivity')
csf_new=$(cat $directory/autotune/profile.json | jq '.csf')
carb_ratio_new=$(cat $directory/autotune/profile.json | jq '.carb_ratio')

# Print Header Info
printf "%-${parameter_width}s| %-${data_width}s| %-${data_width}s| %-${data_width}s\n" "Parameter" "Pump" "Autotune" "Days Missing" >> $report_file
printf "%s\n" "---------------------------------------------------------" >> $report_file

# Print ISF, CSF and Carb Ratio Recommendations
printf "%-${parameter_width}s| %-${data_width}.3f| %-${data_width}.3f|\n" "ISF [mg/dL/U]" $isf_current $isf_new >> $report_file
# if [ $csf_current != null ]; then
  # printf "%-${parameter_width}s| %-${data_width}.3f| %-${data_width}.3f\n" "CSF [mg/dL/g]" $csf_current $csf_new >> $report_file
# else
  # printf "%-${parameter_width}s| %-${data_width}s| %-${data_width}.3f\n" "CSF [mg/dL/g]" "n/a" $csf_new >> $report_file
# fi
printf "%-${parameter_width}s| %-${data_width}.3f| %-${data_width}.3f|\n" "Carb Ratio[g/U]" $carb_ratio_current $carb_ratio_new >> $report_file

# Print Basal Profile Recommendations
printf "%-${parameter_width}s| %-${data_width}s| %-${data_width}s| %-${data_width}s\n" "Basals [U/hr]" "-" "" "" >> $report_file

# Build time_list array of H:M in 30 minute increments to mirror pump basal schedule
time_list=()
minutes_list=()
end_time=23:30
time=00:00
minutes=0
for h in $(seq -w 0 23||gseq -w 0 23); do
    for m in 00 30; do
        time="$h:$m"
        minutes=$(echo "60 * $h + $m" | bc)
        #echo $time $minutes
        time_list+=( "$time" )
        minutes_list+=( "$minutes" )
    done
done

for (( i=0; i<${#minutes_list[@]}; i++ ))
do
  # Check for current entry (account for 1-based index from grep)
  basal_index_current=$(printf "%s\n" ${basal_minutes_current[@]}|grep -nw ${minutes_list[$i]} | sed 's/:.*//')
  if [[ ${#basal_index_current} != 0 ]]; then
    rate_current=${basal_rate_current[$(($basal_index_current - 1))]}
  fi
  # Check for autotune entry (account for 1-based index from grep)
  basal_index_new=$(printf "%s\n" ${basal_minutes_new[@]}|grep -nw ${minutes_list[$i]} | sed 's/:.*//')
  if [[ ${#basal_index_new} != 0 ]]; then
    rate_new=${basal_rate_new[$((${basal_index_new} - 1))]}
	rate_untuned=${basal_untuned_new[$((${basal_index_new} - 1))]}
	if [[ $rate_untuned == "null" ]]; then
		rate_untuned="0"
	fi
  fi
  # Print this basal profile recommend based on data availability at this time
  if [[ ${#basal_index_current} == 0 ]] && [[ ${#basal_index_new} == 0 ]]; then
    printf "  %-$(expr ${parameter_width} - 2)s| %-${data_width}s| %-${data_width}s| %-${data_width}s\n" ${time_list[$i]} "" "" "" >> $report_file
  elif [[ ${#basal_index_current} == 0 ]] && [[ ${#basal_index_new} != 0 ]]; then
    printf "  %-$(expr ${parameter_width} - 2)s| %-${data_width}s| %-${data_width}.3f| %-${data_width}s\n" ${time_list[$i]} "" $rate_new $rate_untuned >> $report_file
  elif [[ ${#basal_index_current} != 0 ]] && [[ ${#basal_index_new} == 0 ]]; then
    printf "  %-$(expr ${parameter_width} - 2)s| %-${data_width}s| %-${data_width}s| %-${data_width}s\n" ${time_list[$i]} $rate_current "" "" >> $report_file
  else
    printf "  %-$(expr ${parameter_width} - 2)s| %-${data_width}.3f| %-${data_width}.3f| %-${data_width}s\n" ${time_list[$i]} $rate_current $rate_new $rate_untuned >> $report_file
  fi
done
