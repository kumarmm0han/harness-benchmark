import {test, expect} from '@playwright/test';

test('AC-E2E-001–004: browser authoring, safety correction, consumer views and immutable history', async ({page, request}) => {
  const id=`BROWSER-${Date.now()}`;
  const author={'X-Demo-User':'demo-author'};
  const consumer={'X-Demo-User':'demo-consumer'};
  await page.goto('/');
  await expect(page.getByRole('status')).toHaveText('SOPs loaded.');
  // Exercise keyboard activation as well as labeled form interaction.
  await page.getByRole('button',{name:'Insert valid template'}).focus();
  await page.keyboard.press('Enter');
  const editor=page.getByLabel('Markdown source');
  const source=(await editor.inputValue()).replaceAll('BILL-REFUND-001',id).replace('Refund for Duplicate Charge',"<img src=x onerror=alert('x')>");
  await page.getByLabel('Draft SOP ID').fill(id);await editor.fill(source);
  await expect(page.getByRole('button',{name:'Publish saved revision'})).toBeDisabled();
  await page.getByRole('button',{name:'Save draft'}).click();await expect(page.getByText('Saved revision 1',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Validate / preview'}).click();await expect(page.getByRole('status')).toHaveText('Validation passed. Preview is ready.');
  await page.getByRole('button',{name:'Publish saved revision'}).click();await expect(page.getByRole('status')).toHaveText('Published version 1.');
  await page.getByRole('combobox',{name:'Domain',exact:true}).selectOption('Billing');await page.getByRole('combobox',{name:'Risk',exact:true}).selectOption('medium');
  await expect(page.getByRole('button',{name:`${id} — <img src=x onerror=alert('x')>`})).toBeVisible();
  const v1=await (await request.get(`/api/v1/sops/${id}`,{headers:consumer})).json();
  expect(v1.version).toBe(1);expect(v1.content.sop_id).toBe(id);
  const invalid=source.replace('  max_amount: 200\n','').replace('escalation:\n  - action_id: A1\n    input: refund_amount\n    op: gt\n    amount: 200\n    target_action_id: A2','escalation: []');
  await editor.fill(invalid);await page.getByRole('button',{name:'Save draft'}).click();await expect(page.getByText('Saved revision 2',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Validate / preview'}).click();
  await expect(page.getByRole('region',{name:'semantic errors'})).toContainText('REFUND_LIMIT');await expect(page.getByRole('region',{name:'semantic errors'})).toContainText('REFUND_ESCALATION');
  await page.getByRole('button',{name:'Publish saved revision'}).click();await expect(page.getByText('Publication failed for revision 2. Previous version 1 remains available.')).toBeVisible();
  expect(await (await request.get(`/api/v1/sops/${id}`,{headers:consumer})).json()).toEqual(v1);
  // Reopen persisted failure after a browser reload.
  await page.reload();await page.getByRole('button',{name:`Open draft ${id}`,exact:true}).click();
  await expect(editor).toHaveValue(invalid);await expect(page.getByText('Publication failed for revision 2. Previous version 1 remains available.')).toBeVisible();
  await editor.fill(source);await page.getByRole('button',{name:'Save draft'}).click();await expect(page.getByText('Saved revision 3',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Publish saved revision'}).click();await expect(page.getByRole('status')).toHaveText('Published version 2.');
  expect(await (await request.get(`/api/v1/sops/${id}/versions/1`,{headers:author})).json()).toEqual(v1);
  await page.getByLabel('Demo-only identity').selectOption('demo-consumer');
  await expect(editor).toHaveCount(0);await page.getByRole('button',{name:`${id} — <img src=x onerror=alert('x')>`}).click();
  const detail=page.getByRole('region',{name:'Published detail'});
  await expect(detail.getByRole('heading',{name:`${id} · Version 2`})).toBeVisible();
  await expect(detail.getByRole('heading',{name:"<img src=x onerror=alert('x')>",exact:true})).toBeVisible();
  await expect(page.locator('img, script:not([src])')).toHaveCount(0);
  await detail.getByRole('button',{name:'AI JSON view'}).click();
  const json=JSON.parse((await page.getByLabel('Canonical JSON').textContent())!);
  expect(json.sop_id).toBe(id);expect(json.version).toBe(2);expect(json.content).toEqual(v1.content);
  // A newer publication while viewing must not change the selected human/JSON snapshot.
  expect((await request.put(`/api/v1/drafts/${id}`,{headers:author,data:{source:source.replace("<img src=x onerror=alert('x')>",'New version')}})).status()).toBe(200);
  const newer=await request.post(`/api/v1/sops/${id}/publish`,{headers:author,data:{revision:4}});expect(newer.status()).toBe(200);expect((await newer.json()).version).toBe(3);
  await detail.getByRole('button',{name:'Human view'}).click();await expect(detail.getByRole('heading',{name:`${id} · Version 2`})).toBeVisible();
  await detail.getByRole('button',{name:'AI JSON view'}).click();expect(JSON.parse((await page.getByLabel('Canonical JSON').textContent())!)).toEqual(json);
  const denied=await request.put(`/api/v1/drafts/${id}`,{headers:consumer,data:{source:'not allowed'}});expect(denied.status()).toBe(403);
});

test('AC-E2E-005: demo seed is discoverable and can be reopened', async ({page}) => {
  await page.goto('/');await page.getByRole('button',{name:'Open draft BILL-REFUND-001',exact:true}).click();
  await expect(page.getByLabel('Markdown source')).toContainText('sop_id: BILL-REFUND-001');
  await page.getByRole('button',{name:'Validate / preview'}).click();await expect(page.getByRole('status')).toHaveText('Validation passed. Preview is ready.');
});
