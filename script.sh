
for i in {0..100}
	do
	./runscripts/smartrun.sh bftsmart.tom.util.ECDSAKeyPairGenerator $i secp256k1 &
done


for i in {1000..1100}
	do
	./runscripts/smartrun.sh bftsmart.tom.util.ECDSAKeyPairGenerator $i secp256k1 &
done