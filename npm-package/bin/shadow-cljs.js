const path = require("path");
const spawn = require('child_process').spawnSync;

var java_args = [
  "-jar",
  path.resolve(__dirname, "shadow-cljs.jar"),
  "--npm"
];

java_args = java_args.concat(process.argv.slice(2));

const java_opts = {
  stdio: 'inherit'
};

function run(java_cmd) {
  return spawn(java_cmd, java_args, java_opts);
}

var result = run("java");

// assume java didn't exist, try node-jre
if (result.error) {
  try {
    var jre = require('node-jre');
    run(jre.driver());
  } catch (err) {
    console.log("please install a java sdk or 'npm install node-jre'");
  }
}


