#!/bin/bash


./runscripts/smartrun.sh bftsmart.demo.microbenchmarks.ThroughputLatencyClient $1 $2 500000 $3 0 false false nosig &
./runscripts/smartrun.sh bftsmart.demo.microbenchmarks.ThroughputLatencyClient $(($1+$2+1)) $2 500000 $3 0 false false nosig &
./runscripts/smartrun.sh bftsmart.demo.microbenchmarks.ThroughputLatencyClient $(($1+(2*$2)+2)) $2 500000 $3 0 false false nosig &
./runscripts/smartrun.sh bftsmart.demo.microbenchmarks.ThroughputLatencyClient $(($1+(3*$2)+3)) $2 500000 $3 0 false false nosig &
