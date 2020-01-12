#!/bin/bash


#sshpass -p quinta scp $1 s11.quinta:/root/alchieri/bftsmart/library-pre-TLS/dist/
while read server
do
echo -e "$server"
sshpass -p quinta scp $2 $server:$3
done < $1

