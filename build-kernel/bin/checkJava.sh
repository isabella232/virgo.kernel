#!/bin/bash


#
# make adjustment when running under cygwin
#
if $cygwin; then
	CLASSPATH=$(cygpath -wp $CLASSPATH)
fi

# Run java version check with the discovered java jvm.
$JAVA_HOME/bin/java \
	-classpath $CLASSPATH \
	org.eclipse.virgo.osgi.launcher.JavaVersionChecker

# If non-zero exit then either we cannot find the check or the java version is incorrect.
if [ $? != 0 ]
then
	exit 1
fi
