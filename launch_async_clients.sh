#!/bin/bash


./runscripts/smartrun.sh bftsmart.demo.microbenchmarks.AsyncLatencyClient $1 $2 500000 $3 $4 false false ecdsa &
./runscripts/smartrun.sh bftsmart.demo.microbenchmarks.AsyncLatencyClient $(($1+$2+1)) $2 500000 $3 $4 false false ecdsa &
./runscripts/smartrun.sh bftsmart.demo.microbenchmarks.AsyncLatencyClient $(($1+(2*$2)+2)) $2 500000 $3 $4 false false ecdsa &
./runscripts/smartrun.sh bftsmart.demo.microbenchmarks.AsyncLatencyClient $(($1+(3*$2)+3)) $2 500000 $3 $4 false false ecdsa &
