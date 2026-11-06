const fs=require('fs');
// La web pinta la expresión con innerHTML (cada número/operador en su <span>, más el cursor): el texto que ve el usuario
// es ese HTML sin etiquetas. El resultado de abajo (calc-prev) sigue siendo textContent.
const els={}; const mk=id=>els[id]||(els[id]={id,_t:'',_h:null,className:'',style:{},scrollTop:0,scrollHeight:0,
  get textContent(){return this._h!==null?this._h.replace(/<[^>]*>/g,''):this._t},set textContent(v){this._t=v;this._h=null},
  get innerHTML(){return this._h===null?'':this._h},set innerHTML(v){this._h=String(v)},
  classList:{_s:new Set(),contains(c){return this._s.has(c)},add(c){this._s.add(c)},remove(c){this._s.delete(c)}}});
global.document={getElementById:mk};
eval(fs.readFileSync('calc_web.js','utf8')+';global.Calc=Calc;');
const out=[];
for(const line of fs.readFileSync('seqs.txt','utf8').split('\n')){ if(!line.trim())continue;
  Calc.tecla('c'); for(const k of line.trim().split(' ')) Calc.tecla(k);
  out.push(els['calc-expr'].textContent+' | '+els['calc-prev'].textContent);
}
fs.writeFileSync('web.out',out.join('\n')+'\n');
