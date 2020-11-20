compile:
	mill -i diplomacy[2.12.12].compile

bsp:
	mill -i mill.contrib.BSP/install

clean:
	git clean -fd

reformat:
	mill -i diplomacy[2.12.12].reformat
	mill -i diplomacy[2.12.12].macros.reformat

checkformat:
	mill -i diplomacy[2.12.12].checkFormat && mill -i diplomacy[2.12.12].macros.checkFormat
