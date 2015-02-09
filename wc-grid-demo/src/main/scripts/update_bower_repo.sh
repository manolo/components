#!/bin/sh
#
# This script clones the bower repo for vaadin-x from github,
# updates the vaadin-x.html file with the last compiled stuff,
# updates vaadin themes and gwt themes, update versions and
# tags the project.
#
# NOTES for public bower register:
#   Registering a package will make it installable to anyone
#   via the registry (https://bower.herokuapp.com), to register
#   it we run:
#   $ bower register vaadin-x git://github.com/manolo/vaadin-x.git
#
#   Deleting the registered project needs to be done by the owner
#   $ curl -X DELETE "https://bower.herokuapp.com/packages/vaadin-x?access_token=<token>"
#   To know the access token go to https://github.com/settings/applications
#
# NOTES for private bower register.
#   We are running a private bower server and a private git instance.
#   So you need this configuration in your local bower preferences file:
#   ~/.bowerrc
#   { "registry": "http://vaadin-x-bower.intra.itmill.com:5678" }
#
#   To register the vaadin-x pakage we did run:
#   $ bower register vaadin-x git://vaadin-x-bower/vaadin-x-bower.git
#
#   To push changes, maven uses ssh://git@vaadin-x-bower:/opt/git/vaadin-x-bower.git
#   You need to add your ssh public key to the admin of the vaadin-x-bower internal server.
#
#

warDir="$1"; shift
modulePrefix="$1"; shift
version="$1"; shift
gitRepo="$1"; shift
package="$1"; shift
vaadinVersion="$1"; shift
moduleName="$1"; shift
aditionalFiles="$*"
modulePath="$warDir/$modulePrefix/$moduleName"
[ -z "$moduleName" ] && echo "Usage $0 <warDir> <modulePrefix> <version> <gitRepo> <package> <vaadinVersion> <moduleName>" && exit 1
[ ! -d "$warDir" ] && echo "warDir does not exist: $warDir" && exit 1

echo "Updating webcomponent '$package $version' in bower repo ($gitRepo)"
currentDir=`pwd`

CloneRepo() {
  ## Create a tmp dir and remove it on exit
  now=`date +%s`
  tmpDir=`mktemp -d /tmp/bower-vaadin-x-$now`
  trap "rm -rf $tmpDir" EXIT

  ## Clone the vaadin web components repo
  git clone $gitRepo $tmpDir || exit 1
}

UpdateModule() {
  ## Copy stuff from the gwt output dir
  cd $modulePath || exit 1
  htmlFile=`ls -t1 *-import.html | head -1`
  [ ! -f $htmlFile ] && echo "No *-import.html file to upload" && exit 1
  cp $htmlFile $tmpDir/$package.html || exit 1

  ## Copy stuff from the war dir
  cp $warDir/demo-$package.html $tmpDir/demo.html || exit 1
  for i in ng-vaadin.js $aditionalFiles
  do
    cp $warDir/$i $tmpDir || exit 1
  done
  tar cf $tmpDir/module.tar \
    deferred \
    >/dev/null 2>&1

  cd $tmpDir || exit 1

  ## Extract files to update
  tar xf module.tar
  rm -f module.tar
  perl -pi -e 's,^.*(nocache|<link).*$,,g' demo.html
  perl -pi -e 's,</head,  <link rel="import" href="'$package'.html"></link>\n</head,' demo.html
  if [ $package = vaadin-grid ]
  then
    perl -pi -e 's,</head,  <link rel="import" href="../vaadin-progress-bar/vaadin-progress-bar.html"></link>\n</head,' demo.html
  fi
  perl -pi -e 's,src="bower_components/,src="../,g' demo.html
}

UpdateVersion() {
  cp src/main/webapp/bower.json $tmpDir || exit 1
  ## Update version, and extract files to update
  cd $tmpDir
  perl -pi -e 's,^.*'$package'.*$,,g' bower.json
  perl -pi -e 's,"version"\s*:\s*"[^"]+","version" : "'$version'",' bower.json
  perl -pi -e 's,"name"\s*:\s*"[^"]+","name" : "'$package'",' bower.json
}

AttachNg() {
  cd $currentDir
  cp ../wc-client/src/main/java/com/vaadin/prototype/wc/gwt/client/js/ng-vaadin.js $tmpDir/vaadin-ng.js || exit 1
  cd $tmpDir
}

AttachObserve() {
  cd $currentDir
  cp src/main/package/web-components/observe-polyfill/Object.observe.poly.js $tmpDir/ || exit 1
  cd $tmpDir
}

AttachThemes() {
  ## Attach Vaadin .css theme files
  mkdir tmpThemes
  cd tmpThemes || exit 1
  themesJar=`find ~/.m2/repository/com/vaadin/ -name "vaadin-themes-$vaadinVersion.jar"`
  if [ -f $themesJar ]
  then
    echo "Extracting $themesJar"
    jar xf $themesJar
    if [ $package = vaadin-valo ]
    then
      cd VAADIN/themes/valo || exit 1
    elif [ $package = vaadin-reindeer ]
    then
      cd VAADIN/themes/reindeer || exit 1
    else
      cd VAADIN/themes || exit 1
    fi
    tar cf $tmpDir/themes.tar . || exit 1
    cd $tmpDir
    rm -rf tmpThemes
    tar xf themes.tar
    rm -f themes.tar
  else
    echo "Unable To find a valid theme .jar for version $vaadinVersion in ~/.m2/repository/com/vaadin/" || exit 1
  fi
}

UpdateRepo() {
  cd $tmpDir
  ## Check if something has been modified
  if git status  --porcelain | grep . >/dev/null
  then
     ## If this version already exists remove it
     if git tag | grep "^v$version$" >/dev/null
     then
        git tag -d v$version || exit 1
        git push origin :refs/tags/v$version || exit 1
     fi

     ## Add new files and commit changes
     git add .
     git commit -m "Upgrading version $version" . || exit 1

     ## If there is something to push, do it
     if ! git diff --cached --exit-code
     then
        git push origin master || exit 1
     fi

     ## Create the version tags
     git tag -a v$version -m "Release $version" || exit 1
     git push origin master --tags || exit 1
  fi
}

echo ">>>> $moduleName"
echo ">>> CloneRepo"  && CloneRepo
echo ">>> UpdateVersion"  && UpdateVersion
[ $moduleName != Themes -a $moduleName != Angular ] && echo ">>> UpdateModule" && UpdateModule
[ $moduleName = Themes ] && echo ">>> AttachThemes $package" && AttachThemes
[ $moduleName = Angular ] && echo ">>> AttachAngular" && AttachNg
[ $moduleName = VaadinComponents -o $moduleName = VaadinGrid ] && echo " >>> AttachObservePolyfill" && AttachObserve
echo ">>> UpdateRepo"  && UpdateRepo

