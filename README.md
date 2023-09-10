# Pathfinder Wiki Golarion Map

This repo is the source code for the interactive map of Golarion used by the Pathfinder wiki.

Full map here: https://pf-wikis.github.io/mapping

One of the main tools used for this is QGIS: https://qgis.org/en/site/

## Acknowledgments

* John Mechalas for starting everthing [here](https://www.dungeonetics.com/golarion-geography/index.html)
* oznogon for continuing this work [here](https://oznogon.com/golarion)
* everyone contributing to the continuation of this project in this repository

## How to check out the source images

The mapping files in this repository are created by tracing information from maps created by Paizo. These images are protected legally and thus can not be publicly shared. To access them follow these steps:

1. You will need access to the private external storage.
2. Clone this git repository.
3. Download and install [DVC](https://dvc.org/).
4. Within the repository root, run ```dvc install```. This will setup some automatic git hooks so that dvc pushes when you push with git.
5. Run ```dvc pull``` to download the images. If this is the first time doing this you will have to authenticate yourself.

## How to Contribute

TODO
