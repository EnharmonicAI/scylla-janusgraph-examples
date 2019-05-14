#!/usr/bin/env bash
# Build and deploy a JanusGraph Docker image to your project Google Container Registry
#
# Assumes that docker is installed & running:
# $ sudo yum install -y docker
# $ sudo systemctl start docker

PROJECT="default-project"

while getopts ":hp:" opt; do
  case $opt in
    h) echo ""
       echo "Build and deploy JanusGraph image"
       echo ""
       echo "Description"
       echo "==========="
       echo "Builds and deploys a JanusGraph Docker image to your project Google Container Registry"
       echo ""
       echo ""
       echo "   Usage   "
       echo "==========="
       echo "-p  GCP Project to be used for deployment (default: $PROJECT). Usage '-p [MyProject]'"
       echo ""
       echo "-h  Display this help message and exit"
       echo ""
       exit 2
       ;;
    p)  PROJECT=$OPTARG ;;
    \?)  echo "Invalid option: -$OPTARG"
        exit 2
        ;;
    :)  echo "Option -$OPTARG requires and argument."
        exit 2
        ;;
  esac
done

git clone https://github.com/JanusGraph/janusgraph-docker.git
cd janusgraph-docker
sudo ./build-images.sh 0.3
sudo docker tag janusgraph/janusgraph:0.3.1 gcr.io/$PROJECT/janusgraph:0.3.1
sudo gcloud auth configure-docker
# Push the image to your project GCR
sudo docker push gcr.io/$PROJECT/janusgraph:0.3.1
