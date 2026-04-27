# Design: Lawn Mowing Service on Post Job

**Date:** 2026-04-27  
**Status:** Approved

## Summary

Add "Mow the lawn" as a selectable service on the Post Job — Step 2 (Services) screen. All requesters see all services; no per-user filtering. Users may combine snow and lawn services in a single job posting.

## Changes

### Frontend — `frontend/src/pages/requester/PostJob.jsx`

Add one entry to the `SERVICES` array:

```js
{
  key: 'lawn', label: 'Mow the lawn',
  sizes: [
    { key: 'small',  label: 'Small',  desc: '< 500 m²',     price: 4000 },
    { key: 'medium', label: 'Medium', desc: '500–1,500 m²',  price: 6500 },
    { key: 'large',  label: 'Large',  desc: '1,500+ m²',     price: 9500 },
  ],
}
```

In `submit()`, add scope mapping:

```js
if (form.services.lawn) scopeSet.add('lawn')
```

### Backend — `backend/src/main/java/com/yosnowmow/service/JobService.java`

Add `"lawn"` to the allowed scope set:

```java
private static final Set<String> VALID_SCOPE = Set.of("driveway", "sidewalk", "both", "lawn");
```

## Out of Scope

- Per-user service filtering based on signup preferences (deferred)
- Visual grouping of snow vs. lawn services (deferred)
- Worker-side lawn job handling / matching logic
