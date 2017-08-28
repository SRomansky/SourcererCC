# Script that is run in the container
cd SourcererCC/clone-detector
gradle jarCDI2  # get latest libs/etc
# scc init scripts
bash runnodes.sh init 1
bash runnodes.sh daemon 1 $1 # pass port..?
