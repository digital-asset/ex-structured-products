#
# Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#

FROM openjdk:8-jre-alpine

WORKDIR /home/sdk

COPY target/structured-products-docker.jar structured-products.jar
COPY target/lib/* /home/sdk/lib/
COPY telegram.properties.sample telegram.properties

ENTRYPOINT java -jar structured-products.jar -s ${SANDBOX_HOST} -p ${SANDBOX_PORT}
