const fs=require('fs');
const els={}; const mk=id=>els[id]||(els[id]={id,textContent:'',className:'',style:{},classList:{_s:new Set(),contains(c){return this._s.has(c)},add(c){this._s.add(c)},remove(c){this._s.delete(c)}}});
global.document={getElementById:mk};
eval(fs.readFileSync('calc_web.js','utf8')+';global.Calc=Calc;');
const out=[];
for(const line of fs.readFileSync('seqs.txt','utf8').split('\n')){ if(!line.trim())continue;
  Calc.tecla('c'); for(const k of line.trim().split(' ')) Calc.tecla(k);
  out.push(els['calc-expr'].textContent+' | '+els['calc-prev'].textContent);
}
fs.writeFileSync('web.out',out.join('\n')+'\n');
