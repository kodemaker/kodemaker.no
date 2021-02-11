#!/bin/bash

set -u

function format-date() {
  timestamp=$1
  format="$2"
  res=$(date -d @$timestamp +"$format" 2> /dev/null)

  if [ $? -eq 0 ]; then
    echo $res
  else
    date -r $timestamp -u +"$format"
  fi
}

role_creds=$(aws sts assume-role \
                 --role-arn "arn:aws:iam::195221715009:role/Deployer" \
                 --role-session-name DeployKodemakerWeb)

prod-env() {
  echo "AWS_ACCESS_KEY_ID=$(echo "$role_creds" | jq -cr .Credentials.AccessKeyId)"
  echo "AWS_SECRET_ACCESS_KEY=$(echo "$role_creds" | jq -cr .Credentials.SecretAccessKey)"
  echo "AWS_SESSION_TOKEN=$(echo "$role_creds" | jq -cr .Credentials.SessionToken)"
}

bucket="s3://kodemaker-www/"
target="build"

#echo "Building site"
#diffs=$(lein build-new-site :json)

#if [ $? -ne 0 ]; then
#  echo "Failed to build site, aborting"
#  exit 1
#fi

#echo "Generating PDFs"
#$(aws ecr get-login --region eu-west-1 --no-include-email)
#docker run --rm -v $(cd $(dirname $0)/build && pwd):/site 575778107697.dkr.ecr.eu-west-1.amazonaws.com/html2pdf:b2d215eee2 /site

#echo "Syncing assets, cacheable for a year"
#pushd "$target" > /dev/null

ts=$(date -d "+1 year" +%s 2> /dev/null)

if [ $? -ne 0 ]; then
  ts=$(date -v +1y +%s)
fi

expires="$(format-date $ts "%a, %d %b %Y %H:%M:%S GMT")"

# TESTING ONLY
echo "Asset expires: $expires"

echo "Check docker"
docker ps

echo "Check s3"
env $(prod-env) aws s3 ls $bucket


#env $(prod-env) aws s3 sync . $bucket --expires "$expires" --exclude "*" --include "assets/*"

#echo "Syncing remaining files and deleting removed files"
#env $(prod-env) aws s3 sync . $bucket --delete --exclude "assets/*"

#echo "Purging old assets"
#env $(prod-env) aws s3 sync . $bucket --expires "$expires" --exclude "*" --include "assets/*" --delete

#env $(prod-env) aws cloudfront create-invalidation --distribution-id E377BQUYES9DH7 --paths / "/*"
