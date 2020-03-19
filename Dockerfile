FROM openjdk:8-jre-alpine

RUN apk --no-cache add bash wget ca-certificates git && apk add --update --no-cache python python3
RUN wget "https://bootstrap.pypa.io/get-pip.py"
RUN python get-pip.py
RUN python3 get-pip.py

ADD requirements.txt requirements.txt
RUN python -m pip install --upgrade --ignore-installed --no-cache-dir -r requirements.txt
RUN python3 -m pip install --upgrade --ignore-installed --no-cache-dir -r requirements.txt

RUN python -m pip uninstall -y pip
RUN python3 -m pip uninstall -y pip
RUN apk del wget ca-certificates git
RUN rm -rf /tmp/* && rm -rf /var/cache/apk/*
