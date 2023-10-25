FROM qgis/qgis:release-3_28

RUN apt-get update \
	&& apt-get install -y mmv openjdk-17-jdk maven jq bc \
	&& rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/felt/tippecanoe.git \
	&& cd tippecanoe \
	&& make -j \
	&& make install

RUN npm install -g geojson-polygon-labels mapshaper @indoorequal/spritezero-cli curve-interpolator@3.0.1

ENV QT_QPA_PLATFORM offscreen

COPY compile.sh /compile.sh
COPY clean.sh /clean.sh
COPY diff.sh /diff.sh
RUN chmod 777 /*.sh

ENTRYPOINT ["/bin/bash", "-c"]