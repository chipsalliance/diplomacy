# registe .gitmodule to local configurations
init:
	cd dependencies
	# clone each repository from .gitmodules if this project is not a git repository
	cat ../.gitmodules|rg -P '(?<=url = ).*' -o|while read repo ; do git clone $repo ; done
	git add dependencies
	git submodule init

# bump all repositories to master(use it at your own risk)
bump:
	git submodule foreach "git fetch --all && git reset --hard origin/master"

bsp:
	mill -i mill.contrib.BSP/install

compile:
	mill -i diplomacy.compile

clean:
	git clean -fd
