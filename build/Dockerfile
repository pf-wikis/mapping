FROM qgis/qgis:3.44
RUN <<EOF
  apt-get update
  apt-get install -y mmv openjdk-21-jdk maven jq bc gcc g++ make libsqlite3-dev zlib1g-dev curl
  rm -rf /var/lib/apt/lists/*
EOF
ENV NVM_DIR=/usr/local/nvm
ENV NODE_VERSION=v24.13.0
RUN <<EOF
	mkdir -p $NVM_DIR
  curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
  ls -lha $NVM_DIR
  \. "$NVM_DIR/nvm.sh"
  nvm install $NODE_VERSION
  nvm use --delete-prefix $NODE_VERSION
EOF
ENV NODE_PATH=$NVM_DIR/versions/node/$NODE_VERSION/lib/node_modules
ENV PATH=$NVM_DIR/versions/node/$NODE_VERSION/bin:$PATH

RUN <<EOF
  git clone https://github.com/felt/tippecanoe.git
  cd tippecanoe
  make -j
  make install
  cd ..
  rm -rf tippecanoe
EOF

RUN npm install -g mapshaper @indoorequal/spritezero-cli

RUN <<EOF
  curl -L https://github.com/koordinates/kart/releases/download/v0.16.1/kart_0.16.1_amd64.deb -o kart.deb 
  dpkg -i kart.deb
  rm -f kart.deb
EOF
ENV QT_QPA_PLATFORM=offscreen

USER 1000:1000

WORKDIR /w
COPY --link --chown=1000:1000 tile-compiler /w/tile-compiler
RUN cd tile-compiler && mvn compile package

COPY --link --chown=1000:1000 frontend /w/frontend

COPY --link --chown=1000:1000 build/compile.sh /w/compile.sh
RUN chmod 777 /w/compile.sh

COPY --link --chown=1000:1000 sources /w/sources



ENTRYPOINT ["/w/compile.sh"]