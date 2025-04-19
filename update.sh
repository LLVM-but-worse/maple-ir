cd $(dirname $0)
rm -rf ./upstream ./org.mapleir.parent
git clone https://github.com/skidfuscatordev/skidfuscator-java-obfuscator.git upstream
cp -r ./upstream/org.mapleir.parent ./org.mapleir.parent
cat ./portal/pre.gradle > ./build.gradle
cat ./upstream/build.gradle >> ./build.gradle
cat ./portal/build2.gradle >> ./build.gradle
cp ./upstream/jitpack.yml ./jitpack.yml
cat ./upstream/settings.gradle | sed -e 's/skidfuscator-master/mapleir-main/g' | sed -e 's/void initBase()/void unused()/g' | sed -e 's/initBase()//g' > ./settings.gradle