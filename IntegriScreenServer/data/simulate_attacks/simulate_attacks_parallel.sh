#!/bin/bash
now=`date '+%Y_%m_%d__%H_%M_%S'`;
logfilename="browser_attack_log_$now.txt"
python user_simulation.py all.txt --attacker parallel --attack_type replace_bunch > logs/$logfilename
