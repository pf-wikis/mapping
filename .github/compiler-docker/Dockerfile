FROM qgis/qgis:release-3_28

RUN apt-get update \
	&& apt-get install -y mmv openjdk-17-jdk maven jq bc \
	&& rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/mapbox/tippecanoe.git \
	&& cd tippecanoe \
	&& make -j \
	&& make install

RUN npm install -g geojson-polygon-labels mapshaper @elastic/spritezero-cli curve-interpolator

ENV QT_QPA_PLATFORM offscreen

COPY compile.sh /compile.sh
COPY clean.sh /clean.sh
COPY diff.sh /diff.sh
RUN chmod 777 /*.sh

ENTRYPOINT ["/bin/bash", "-c"]