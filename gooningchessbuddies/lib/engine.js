export const N=14,F=[[-1,0],[0,1],[1,0],[0,-1]];
export const ok=(r,c)=>r>=0&&c>=0&&r<N&&c<N&&!((r<3||r>10)&&(c<3||c>10));
const O=[[1,0],[-1,0],[0,1],[0,-1]],D=[[1,1],[1,-1],[-1,1],[-1,-1]],K=[[1,2],[2,1],[-1,2],[-2,1],[1,-2],[2,-1],[-1,-2],[-2,-1]];
const dep=(s,r,c)=>[13-r,c,r,13-c][s];
export function initial(seats){
  const board=Array(196).fill(null),B="RNBQKBNR";
  seats.forEach((id,s)=>{if(!id)return;for(let x=0;x<8;x++)for(let d=0;d<2;d++){
    const [r,c]=[[13-d,3+x],[3+x,d],[d,10-x],[10-x,13-d]][s];board[r*N+c]=s+(d?"P":B[x]);}});
  return {kind:"multi",seats,alive:seats.map(Boolean),turn:0,board,last:null,n:0,half:0};
}
export function movesFrom(st,r,c){
  const p=st.board[r*N+c];if(!p)return[];const s=+p[0],t=p[1],o=[],at=(a,b)=>st.board[a*N+b];
  const can=(a,b)=>ok(a,b)&&(!at(a,b)||+at(a,b)[0]!==s);
  const slide=ds=>ds.forEach(([a,b])=>{let x=r+a,y=c+b;while(ok(x,y)){const q=at(x,y);if(q){if(+q[0]!==s)o.push([x,y]);break}o.push([x,y]);x+=a;y+=b}});
  const step=ds=>ds.forEach(([a,b])=>can(r+a,c+b)&&o.push([r+a,c+b]));
  if(t=="P"){const [a,b]=F[s],free=(x,y)=>ok(x,y)&&!at(x,y);
    if(free(r+a,c+b)){o.push([r+a,c+b]);if(dep(s,r,c)==1&&free(r+2*a,c+2*b))o.push([r+2*a,c+2*b])}
    for(const k of[-1,1]){const x=r+a+(a?0:k),y=c+b+(a?k:0);if(ok(x,y)&&at(x,y)&&+at(x,y)[0]!==s)o.push([x,y])}}
  else if(t=="N")step(K);else if(t=="K")step([...O,...D]);else{if(t!="B")slide(O);if(t!="R")slide(D)}
  return o;
}
export function eliminate(st,s){st.alive[s]=false;for(let i=0;i<196;i++)if(st.board[i]&&+st.board[i][0]==s)st.board[i]=null}
export function advance(st){let k=st.turn;for(let i=0;i<4;i++){k=(k+1)%4;if(st.alive[k]){st.turn=k;break}}}
export function apply(st,fr,fc,tr,tc){
  const s2=structuredClone(st),b=s2.board,p=b[fr*N+fc],s=+p[0],cap=b[tr*N+tc];
  b[fr*N+fc]=null;b[tr*N+tc]=p[1]=="P"&&dep(s,tr,tc)>=8?s+"Q":p;
  s2.half=cap||p[1]=="P"?0:st.half+1;s2.last=[fr,fc,tr,tc];s2.n++;
  if(cap&&cap[1]=="K")eliminate(s2,+cap[0]);
  advance(s2);return s2;
}
export function winner(st){const a=st.alive.map((x,i)=>x?i:-1).filter(i=>i>=0);return a.length==1?a[0]:st.half>=200?-2:-1}
