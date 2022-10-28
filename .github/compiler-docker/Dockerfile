FROM qgis/qgis:release-3_28

RUN apt-get update \
	&& apt-get install -y mmv openjdk-17-jdk maven jq \
	&& rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/mapbox/tippecanoe.git \
	&& cd tippecanoe \
	&& make -j \
	&& make install

RUN npm install -g geojson-polygon-labels mapshaper @elastic/spritezero-cli

ENV QT_QPA_PLATFORM offscreen

COPY entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]