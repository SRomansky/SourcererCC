# TODO get docker to launch daemon automatically? It can be done with manual commands

# TODO automatically configure the port, dataset_dir, queryset_dir, and output_dir

port='4568'

dataset_dir=`pwd`'/../clone-detector/input/dataset'
queryset_dir=`pwd`'/../clone-detector/input/dataset'
output_dir=`pwd`'/../clone-detector/NODE_1'
docker build -t local-scc .
docker run -it -p $port:4568 -v $dataset_dir:/home/SourcererCC/clone-detector/input/dataset -v $queryset_dir:/home/SourcererCC/clone-detector/input/queryset -v $output_dir:/home/SourcererCC/clone-detector/NODE_1 local-scc $port
