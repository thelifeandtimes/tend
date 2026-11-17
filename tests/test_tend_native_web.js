const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const source = fs.readFileSync(require("node:path").join(__dirname,"../code/nex/tend/index.html"),"utf8").split("<script>")[1].split("</script>")[0];

async function fixture(outcome="committed") {
  const nodes = Object.fromEntries(["identity","status","lists","notes","new-list","new-note","grant"].map(id=>[id,{textContent:"",className:"",innerHTML:"",elements:{},closest:()=>({})}]));
  let focused=false, serial=0;
  nodes["new-note"].elements={host:{value:""},resource:{value:""},title:{value:"",focus(){focused=true}}};
  const storage = new Map(), requests=[];
  const context = {
    document:{getElementById:id=>nodes[id]}, location:{pathname:"/apps/tend"},
    localStorage:{getItem:key=>storage.get(key)||null,setItem:(key,value)=>storage.set(key,value),removeItem:key=>storage.delete(key)},
    crypto:{randomUUID:()=>`op-${++serial}`}, setInterval:()=>{}, confirm:()=>true,
    fetch:async(url,options={})=>{
      requests.push({url,options}); let value;
      if(url.endsWith("whoami"))value="~zod";
      else if(url.endsWith("state"))value={snapshot:{"protocol-version":3,lists:[{id:1,title:"Chores",revision:1,reminders:[{id:2,title:"Dishes",completed:false}]}]}};
      else if(url.endsWith("private"))value=[];
      else if(url.endsWith("command")){
        if(outcome==="lost")throw Error("reply lost");
        const command=JSON.parse(options.body),id=command.kind==="edit"?Object.values(command.action)[0]["operation-id"]:command["operation-id"];
        value={protocol:"tend-native-1","operation-id":id,outcome,reason:"ok"};
      } else value=null;
      return {ok:true,json:async()=>value};
    }
  };
  vm.runInNewContext(source,context);
  await new Promise(setImmediate);
  return {context,nodes,storage,requests,focused:()=>focused};
}

test("native quick create reads form controls and sends one canonical command",async()=>{
  const f=await fixture();
  // HTMLFormElement.title is its title attribute, not its named input.
  f.nodes["new-list"].onsubmit({preventDefault(){},target:{title:"",elements:{title:{value:"My list"}}}});
  await new Promise(setImmediate);
  const commands=f.requests.filter(r=>r.url.endsWith("command"));
  assert.equal(commands.length,1);
  assert.equal(JSON.parse(commands[0].options.body).action["create-list"].title,"My list");
  assert.equal(f.storage.size,0);
  assert.equal(f.nodes.status.textContent,"Saved");
});

test("private item selection uses a logical task reference and focuses its control",async()=>{
  const f=await fixture();
  f.nodes.lists.onclick({target:{closest:()=>({dataset:{note:"1/2"}})}});
  assert.equal(f.nodes["new-note"].elements.host.value,"~zod");
  assert.equal(f.nodes["new-note"].elements.resource.value,"lists/1/tasks/2");
  assert.equal(f.focused(),true);
});

test("lost command reply retains the exact command and refuses a replacement",async()=>{
  const f=await fixture("lost");
  const event={preventDefault(){},target:{elements:{title:{value:"Keep this intent"}}}};
  f.nodes["new-list"].onsubmit(event);
  await new Promise(setImmediate);
  assert.equal(f.storage.size,1);
  const held=JSON.parse([...f.storage.values()][0]);
  assert.equal(held.action["create-list"]["operation-id"],"op-1");
  f.nodes["new-list"].onsubmit(event);
  await new Promise(setImmediate);
  assert.equal(f.requests.filter(r=>r.url.endsWith("command")).length,1);
  assert.equal(JSON.parse([...f.storage.values()][0]).action["create-list"]["operation-id"],"op-1");
});
