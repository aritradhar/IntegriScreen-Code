#!/bin/bash

for ((i=1; i<=$1; i++)) do
    cp bank_transfer.json bank_transfer_$i.json
    sed -i -e "s/bank_transfer/bank_transfer_$i/g" bank_transfer_$i.json
    sed -i -e "s/Bank Transfer/Bank Transfer $i/g" bank_transfer_$i.json
    echo $i
done
