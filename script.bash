#!/bin/bash

sudo tcpdump -nn -i any 'ip' -l


#sudo tcpdump -nn -i any -l -s 0 -v ip
#do
##  echo "<$line>"
##  exit 1
#  if [[ "$line" =~ ([0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{6})\ IP.*proto\ TCP.*length\ ([0-9]+) ]]
#  then
#    src_ip=${BASH_REMATCH[1]}
#    dest_ip=${BASH_REMATCH[2]}
#    length=${BASH_REMATCH[3]}
#
#    echo "$src_ip -- $dest_ip -- $length"
#  fi
#done

##!/bin/bash
#
#INTERFACE="any"
#TOTAL_LENGTH=0
#
#sudo tcpdump -nn -i $INTERFACE -l -s 0 -v | while IFS= read -r line
#do
#  if [[ "$line" =~ .*length\ ([0-9]+).* ]]
#  then
#    LENGTH=${BASH_REMATCH[1]}
#    TOTAL_LENGTH=$((TOTAL_LENGTH + LENGTH))
#    echo "Current total length: $TOTAL_LENGTH"
#  fi
#done