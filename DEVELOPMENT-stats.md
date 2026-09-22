
```bash
javaLOC=$( find . -name \*.java -exec cat {} \; | wc -l )
echo "number of Java lines of code: ${javaLOC}" 

htmlLOC=$( find src/main/webapp/src  -name \*.html -exec cat {} \; | wc -l )
echo "number of Html lines of code: ${htmlLOC}" 

typescriptGeneratedLOC=$(find src/main/webapp/src/app/rest  -name \*.ts -exec cat {} \; | wc -l )
echo "number of TypeScript Generated lines of code: ${typescriptGeneratedLOC}"

typescriptLOC=$( find src/main/webapp/src -path src/main/webapp/src/app/rest -prune -o -name \*.ts -exec cat {} \; | wc -l )
echo "number of TypeScript lines of code: ${typescriptLOC}"
```

=>
result as of 2026-09-23  (=D+18 of started September 5th, vibed-coded in 12 evenings + weekend, but mostly waiting the throttled data scrapping)
```text
number of Java lines of code: 12986
number of Html lines of code: 3085
number of TypeScript Generated lines of code: 7230
number of TypeScript lines of code: 6158
```

showing only active days of developments (via git commit days)
```bash
git log | grep Date | sed '/^Date:/ { s/ ..:..:..//; s/ [+-]....$// }' | uniq | wc -l
```
=> 12 

