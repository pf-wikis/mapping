FROM qgis/qgis:3.40

RUN apt-get update \
	&& apt-get install -y mmv openjdk-21-jdk maven jq bc gcc g++ make libsqlite3-dev zlib1g-dev nodejs npm curl \
	&& rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/felt/tippecanoe.git \
	&& cd tippecanoe \
	&& make -j \
	&& make install \
	&& cd .. \
	&& rm -rf tippecanoe

RUN npm install -g mapshaper @indoorequal/spritezero-cli

RUN curl -L https://github.com/koordinates/kart/releases/download/v0.16.1/kart_0.16.1_amd64.deb -o kart.deb \
	&& dpkg -i kart.deb \
	&& rm -f kart.deb

ENV QT_QPA_PLATFORM offscreen

USER 1000:1000

WORKDIR /w
COPY --link --chown=1000:1000 tile-compiler /w/tile-compiler
RUN cd tile-compiler \
  && mvn compile package

COPY --link --chown=1000:1000 frontend /w/frontend

COPY --link --chown=1000:1000 build/compile.sh /w/compile.sh
RUN chmod 777 /w/compile.sh

COPY --link --chown=1000:1000 sources /w/sources



ENTRYPOINT ["/w/compile.sh"]