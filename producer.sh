#!/bin/bash

cities=("dublin" "seattle" "sydney" "tokyo" "paris")

for i in {1..20000}
do
  USER_ID="user${i}"

  size=${#cities[@]}
  index=$(($RANDOM % $size))
  city=${cities[$index]}

  # put item in dynamodb table using aws cli
  aws dynamodb put-item --table-name $TABLE_NAME --item '{"email": {"S": "'$USER_ID'@foo.com"}, "name": {"S": "'$USER_ID'"}, "city": {"S": "'$city'"}}'

  echo "Added user $USER_ID"
done
