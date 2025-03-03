FROM qgis/qgis:release-3_34

RUN apt-get update \
	&& apt-get install -y mmv openjdk-21-jdk maven jq bc \
	&& rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/felt/tippecanoe.git \
	&& cd tippecanoe \
	&& make -j \
	&& make install

RUN npm install -g mapshaper @indoorequal/spritezero-cli

RUN curl -L https://github.com/koordinates/kart/releases/download/v0.14.2/kart_0.14.2_amd64.deb -o kart.deb \
	&& dpkg -i kart.deb \
	&& rm -f kart.deb

ENV QT_QPA_PLATFORM offscreen

COPY compile.sh /compile.sh
RUN chmod 777 /*.sh

# this should reduce compile time by already downloading maven dependencies
RUN git clone https://github.com/pf-wikis/mapping.git mapping \
  && cd mapping/tile-compiler \
  && mvn compile package \
  && cd ../.. \
  && rm -rf mapping

ENTRYPOINT ["/bin/bash", "-c"]