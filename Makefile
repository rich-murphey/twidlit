DATA=data/about.html data/icon.gif data/intro.html data/pref.html data/ref.html pref/chords.cfg.txt pref/TwidlitDuplicates.txt pref/TwidlitKeyEvents.txt pref/TwidlitKeyNames.txt pref/TwidlitKeyValues.txt pref/TwidlitLost.txt pref/TwidlitNGrams.txt pref/TwidlitPersist.properties pref/TwidlitPreferences.txt pref/TwidlitUnprintables.txt
JAR_DATA=${DATA}
IO=pkp/io/Io.class pkp/io/LineReader.class pkp/io/SpacedPairReader.class
STRING=pkp/string/StringSource.class pkp/string/StringInt.class pkp/string/StringsInts.class pkp/string/StringsIntsBuilder.class
LOOKUP=pkp/lookup/LookupBuilder.class pkp/lookup/LookupImplementation.class pkp/lookup/LookupSet.class pkp/lookup/LookupSetBuilder.class pkp/lookup/LookupTable.class pkp/lookup/LookupTableBuilder.class pkp/lookup/SharedIndex.class
UI=pkp/ui/ControlDialog.class pkp/ui/ControlWindow.class pkp/ui/ExtensionFileFilter.class pkp/ui/HtmlWindow.class pkp/ui/IntegerTextField.class pkp/ui/LabelComponentBox.class pkp/ui/PersistentDialog.class pkp/ui/PersistentFrame.class pkp/ui/PersistentMenuBar.class pkp/ui/ProgressWindow.class pkp/ui/SaveTextWindow.class pkp/ui/SliderBuilder.class pkp/ui/TextWindow.class
UTIL=pkp/util/Log.class pkp/util/Persist.class pkp/util/Persistent.class pkp/util/PersistentProperties.class pkp/util/Pref.class 
CHARS=pkp/chars/CharCounts.class pkp/chars/Counts.class pkp/chars/NGram.class pkp/chars/NGrams.class
TWIDDLE=pkp/twiddle/Assignment.class pkp/twiddle/Chord.class pkp/twiddle/KeyMap.class pkp/twiddle/KeyPress.class pkp/twiddle/KeyPressList.class pkp/twiddle/Modifiers.class pkp/twiddle/ThumbKeys.class pkp/twiddle/Twiddle.class 
TWIDDLER=pkp/twiddler/Cfg.class pkp/twiddler/Settings.class pkp/twiddler/SettingsWindow.class 
TWIDLIT=pkp/twidlit/ChordSource.class pkp/twidlit/ChordTimes.class pkp/twidlit/CountsRangeSetter.class pkp/twidlit/Hand.class pkp/twidlit/SortedChordTimes.class pkp/twidlit/TwiddlerWaitSetter.class pkp/twidlit/TwiddlerWindow.class pkp/twidlit/Twidlit.class pkp/twidlit/TwidlitMenu.class
CLASSES= ${TEST} ${IO} ${STRING} ${LOOKUP} ${UI} ${UTIL} ${CHARS} ${TWIDDLE} ${TWIDDLER} ${TWIDLIT}
CLEAN=rm *.class *~ *.bak tmp
QUIET_CLEAN=${CLEAN} 2> /dev/null
QUIET_CLEAN_AND_BACK=${QUIET_CLEAN}; cd - > /dev/null

all: classes
jar: Twidlit.jar
io: ${IO}
string: ${STRING}
lookup: ${LOOKUP}
ui: ${UI}
util: ${UTIL}
chars: ${CHARS}
twiddle: ${TWIDDLE}
twiddler: ${TWIDDLER}
twidlit: ${TWIDLIT}
classes: io string lookup ui util chars twiddle twiddler twidlit
clean:
	@cd data; ${QUIET_CLEAN_AND_BACK}
	@cd pref; ${QUIET_CLEAN_AND_BACK}
	@cd script; ${QUIET_CLEAN_AND_BACK}
	@cd pkp/io; ${QUIET_CLEAN_AND_BACK}
	@cd pkp/string; ${QUIET_CLEAN_AND_BACK}
	@cd pkp/lookup; ${QUIET_CLEAN_AND_BACK}
	@cd pkp/ui; ${QUIET_CLEAN_AND_BACK}
	@cd pkp/util; ${QUIET_CLEAN_AND_BACK}
	@cd pkp/chars; ${QUIET_CLEAN_AND_BACK}
	@cd pkp/twiddle; ${QUIET_CLEAN_AND_BACK}
	@cd pkp/twiddler; ${QUIET_CLEAN_AND_BACK}
	@cd pkp/twidlit; ${QUIET_CLEAN_AND_BACK}
	@${QUIET_CLEAN} pref/TwidlitPreferences.txt classlist.txt TwidlitLog.txt TwidlitPersist.properties ||:

%.class: %.java
	javac $<

pref/TwidlitPreferences.txt: data/pref.html script/makePrefs.sh
	@script/makePrefs.sh data/pref.html pref/TwidlitPreferences.tmp
	@diff pref/TwidlitPreferences.txt pref/TwidlitPreferences.tmp 2> /dev/null ||:
	@mv pref/TwidlitPreferences.tmp pref/TwidlitPreferences.txt
   
# list of the classes to jar, escaping the $s
classlist.txt: ${CLASSES}
	find . -iname \*.class | sed 's/\$$/\\$$/' > $@

Twidlit.jar: Manifest.txt ${JAR_DATA} classlist.txt
	jar cfm $@ Manifest.txt ${JAR_DATA} $(shell cat classlist.txt)
	mv Twidlit.jar ../Program\ Files/Twidlit

