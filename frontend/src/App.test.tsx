import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {vi, test, expect} from 'vitest';
import App from './App';
import {Detail} from './Detail';
import type {Snapshot} from './api';
import template from './template.md?raw';

const snapshot: Snapshot = {sop_id:'BILL-REFUND-001', version:1, published_at:'2026-01-01T12:00:00Z', content:{sop_id:'BILL-REFUND-001',title:'Refund policy',owner_team:'Billing Operations',domain:'Billing',intent:'refund_duplicate_charge',risk_level:'medium',max_autonomy:'assist',policy:{use_when:["<img src=x onerror=alert('x')>"],do_not_use_when:['Fraud suspected']}, inputs:[{name:'refund_amount',type:'number'}],rules:[{id:'R1',conditions:[{input:'refund_amount',op:'lte',value:200}],action_ids:['A1']}],actions:[{id:'A1',kind:'refund',description:'Policy action description',max_amount:200}],boundaries:{escalation:[{action_id:'A1',input:'refund_amount',op:'gt',amount:200,target_action_id:'A2'}]},customer_messages:{primary:'Example primary',escalation:'Example escalation'}}};
function setupApi(failSave = false) {
  let revision=0; let source=''; let published=false;
  const fetchMock=vi.fn(async (url: string, init: RequestInit) => {
    let data: unknown = []; let status=200;
    const body=init.body ? JSON.parse(String(init.body)) : {};
    if (url.includes('/drafts/') && init.method === 'PUT') {if(failSave) {status=500;data={message:'Save failed',issues:[]}} else {source=body.source;revision++;data={sop_id:'BILL-REFUND-001',source,revision}}}
    else if(url.endsWith('/drafts')) data=revision ? [{sop_id:'BILL-REFUND-001',revision,failed_revision:null,current_version:published?1:null}] : [];
    else if(url.includes('/drafts/')) data={sop_id:'BILL-REFUND-001',source,revision,failed_revision:null,current_version:published?1:null};
    else if(url.endsWith('/validate')) data={valid:true,issues:[],content:snapshot.content};
    else if(url.endsWith('/publish')) {published=true;data=snapshot}
    else if(url.includes('/sops?')) data=published ? [{sop_id:snapshot.sop_id,title:snapshot.content.title,version:1,domain:'Billing',risk:'medium'}] : [];
    else if(url.endsWith('/sops/BILL-REFUND-001')) data=snapshot;
    return {ok:status===200,status,json:async()=>data} as Response;
  });
  vi.stubGlobal('fetch',fetchMock);return fetchMock;
}
test('human and JSON use identical snapshot and hostile HTML remains text', async () => {
  const {container}=render(<Detail snapshot={snapshot}/>);
  expect(screen.getByText("<img src=x onerror=alert('x')>")).toBeInTheDocument();expect(container.querySelector('img')).toBeNull();
  expect(screen.getByText('No action is executed by this app.')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button',{name:'AI JSON view'}));
  expect(JSON.parse(screen.getByLabelText('Canonical JSON').textContent!)).toEqual(snapshot);
  await userEvent.click(screen.getByRole('button',{name:'Human view'}));expect(screen.getByText('BILL-REFUND-001 · Version 1')).toBeInTheDocument();
});
test('author inserts, saves, validates and publishes; unsaved edits disable publication', async () => {
  const fetchMock=setupApi();render(<App/>);await screen.findByText('SOPs loaded.');
  await userEvent.click(screen.getByRole('button',{name:'Insert valid template'}));expect(screen.getByLabelText('Markdown source')).toHaveValue(template);
  expect(screen.getByRole('button',{name:'Publish saved revision'})).toBeDisabled();
  await userEvent.click(screen.getByRole('button',{name:'Save draft'}));await screen.findByText('Saved revision 1');
  await userEvent.click(screen.getByRole('button',{name:'Validate / preview'}));await screen.findByText('Validation passed. Preview is ready.');
  await userEvent.click(screen.getByRole('button',{name:'Publish saved revision'}));await screen.findByText('Published version 1.');
  expect(fetchMock.mock.calls.find(([url])=>url.endsWith('/publish'))?.[1].body).toBe('{"revision":1}');
  fireEvent.change(screen.getByLabelText('Markdown source'),{target:{value:template+'\n'}});expect(screen.getByText('Unsaved changes')).toBeInTheDocument();expect(screen.getByRole('button',{name:'Publish saved revision'})).toBeDisabled();
});
test('save failure preserves editor content', async () => {
  setupApi(true);render(<App/>);await screen.findByText('SOPs loaded.');
  fireEvent.change(screen.getByLabelText('Markdown source'),{target:{value:'my unfinished policy'}});await userEvent.click(screen.getByRole('button',{name:'Save draft'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('Save failed');expect(screen.getByLabelText('Markdown source')).toHaveValue('my unfinished policy');expect(screen.getByRole('button',{name:'Publish saved revision'})).toBeDisabled();
});
test('consumer has no author controls, filters use AND, and detail views fetch once', async () => {
  const fetchMock=setupApi();render(<App/>);await screen.findByText('SOPs loaded.');await userEvent.click(screen.getByRole('button',{name:'Insert valid template'}));await userEvent.click(screen.getByRole('button',{name:'Save draft'}));await screen.findByText('Saved revision 1');await userEvent.click(screen.getByRole('button',{name:'Publish saved revision'}));await screen.findByText('Published version 1.');
  await userEvent.selectOptions(screen.getByLabelText('Demo-only identity'),'demo-consumer');await screen.findByText('SOPs loaded.');expect(screen.queryByLabelText('Markdown source')).toBeNull();expect(screen.queryByRole('button',{name:'Save draft'})).toBeNull();
  await userEvent.selectOptions(screen.getByLabelText('Domain'),'Billing');await userEvent.selectOptions(screen.getByLabelText('Risk'),'medium');
  await waitFor(()=>expect(fetchMock.mock.calls.some(([url])=>url.endsWith('domain=Billing&risk=medium'))).toBe(true));
  await userEvent.click(screen.getByRole('button',{name:'BILL-REFUND-001 — Refund policy'}));await screen.findByText('Published snapshot loaded.');
  const calls=fetchMock.mock.calls.length;await userEvent.click(screen.getByRole('button',{name:'AI JSON view'}));expect(JSON.parse(screen.getByLabelText('Canonical JSON').textContent!)).toEqual(snapshot);expect(fetchMock).toHaveBeenCalledTimes(calls);
});
test('validation shows structural and semantic groups with paths', async () => {
  setupApi();render(<App/>);await screen.findByText('SOPs loaded.');
  vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({valid:false,content:null,issues:[{stage:'structural',path:'inputs',code:'TYPE',message:'Expected a list.'},{stage:'semantic',path:'actions[0].max_amount',code:'REFUND_LIMIT',message:'Declare a refund limit.'}]})})));
  await userEvent.click(screen.getByRole('button',{name:'Validate / preview'}));expect(await screen.findByRole('region',{name:'structural errors'})).toHaveTextContent('inputs');expect(screen.getByRole('region',{name:'semantic errors'})).toHaveTextContent('actions[0].max_amount');
});
test('reopened failed draft reports retained publication and failed publish preserves the source', async () => {
  const issue={stage:'semantic',path:'boundaries.escalation',code:'REFUND_ESCALATION',message:'Declare matching escalation.'};
  vi.stubGlobal('fetch',vi.fn(async (url: string) => {
    const draft={sop_id:'BILL-REFUND-001',revision:2,failed_revision:2,current_version:1,source:'invalid saved replacement'};
    let data: unknown=[];let ok=true;
    if(url.endsWith('/drafts')) data=[draft];
    else if(url.includes('/drafts/')) data=draft;
    else if(url.endsWith('/publish')) {ok=false;data={message:'Publication failed.',issues:[issue]}}
    return {ok,json:async()=>data};
  }));
  render(<App/>);await userEvent.click(await screen.findByRole('button',{name:'Open draft BILL-REFUND-001'}));
  await screen.findByText('Opened revision 2.');expect(screen.getByRole('alert')).toHaveTextContent('Previous version 1 remains available.');
  await userEvent.click(screen.getByRole('button',{name:'Publish saved revision'}));
  expect(await screen.findByRole('region',{name:'semantic errors'})).toHaveTextContent('Declare matching escalation.');expect(screen.getByLabelText('Markdown source')).toHaveValue('invalid saved replacement');
});
