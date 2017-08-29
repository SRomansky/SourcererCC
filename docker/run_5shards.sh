# Get source
git clone -b inmemcc_fork https://github.com/SRomansky/SourcererCC.git
cd SourcererCC
git pull
cd ..
cp build_run.sh SourcererCC

# TODO get docker to launch daemon automatically? It can be done with manual commands

# TODO automatically configure the port, dataset_dir, queryset_dir, and output_dir
# TODO need parameter for server ip/port

port=4568
n=0

repo=`pwd`'/SourcererCC'
dataset_dir=`pwd`'/../clone-detector/input/dataset'
queryset_dir=`pwd`'/../clone-detector/input/dataset'
output_dir=`pwd`'/../clone-detector/NODE_1'
docker build -t local-scc .
docker run --net host -v $repo:/home/SourcererCC -v $dataset_dir:/home/SourcererCC/clone-detector/input/dataset -v $queryset_dir:/home/SourcererCC/clone-detector/input/queryset -v $output_dir:/home/SourcererCC/clone-detector/NODE_1 -it local-scc ../build_run.sh $port # run script in thing

# todo cd SourcererCC/clone-detector
# todo run gradle jarCDI2
# todo run the init commands for scc
# todo run the daemno
