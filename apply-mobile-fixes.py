#!/usr/bin/env python3
def replace_in_file(filepath, old, new, desc):
    with open(filepath, 'r') as f:
        content = f.read()
    if old not in content:
        print(f"  WARNING: not found: {desc}")
        return False
    content = content.replace(old, new, 1)
    with open(filepath, 'w') as f:
        f.write(content)
    print(f"  OK: {desc}")
    return True

base = "/opt/jelu/src/jelu-ui/src"

print("\n=== Fix 1: style.css ===")
replace_in_file(f"{base}/assets/style.css",
".edit-modal {\n  margin: 30px;\n  width: min(900px, 88vw);\n  @apply rounded-xl;\n}\n\n.event-modal {\n  margin: 30px;\n  height: min(600px, 70vh);\n  width: min(350px, 90vw);\n  @apply rounded-xl;\n}\n\n.author-modal {\n  @apply m-2 sm:p-5 rounded-xl;\n  width: min(600px, 90vw);\n}\n\n.quote-modal {\n  @apply m-5 sm:p-5 rounded-xl;\n  height: min(700px, 75vh);\n  width: min(900px, 85vw);\n}\n\n.review-modal {\n  @apply m-5 sm:p-5 rounded-xl;",
".edit-modal {\n  margin: 12px;\n  width: min(900px, 94vw);\n  max-height: 90vh;\n  overflow-y: auto;\n  @apply rounded-xl p-3 sm:p-5;\n}\n\n.event-modal {\n  margin: 12px;\n  height: auto;\n  max-height: 85vh;\n  width: min(380px, 94vw);\n  overflow-y: auto;\n  @apply rounded-xl p-4;\n}\n\n.author-modal {\n  margin: 12px;\n  width: min(600px, 94vw);\n  max-height: 90vh;\n  overflow-y: auto;\n  @apply p-3 sm:p-5 rounded-xl;\n}\n\n.quote-modal {\n  margin: 12px;\n  height: auto;\n  max-height: 85vh;\n  width: min(900px, 94vw);\n  overflow-y: auto;\n  @apply p-3 sm:p-5 rounded-xl;\n}\n\n.review-modal {\n  margin: 12px;\n  height: auto;\n  max-height: 85vh;\n  width: min(900px, 94vw);\n  overflow-y: auto;\n  @apply p-3 sm:p-5 rounded-xl;",
"Mobile modals")

print("\n=== Fix 2: App.vue ===")
replace_in_file(f"{base}/App.vue",
'    <div class="divider mt-0" />\n    <router-view />',
'    <div class="divider mt-0" />\n    <div class="px-2 sm:px-4 lg:px-6">\n      <router-view />\n    </div>',
"Router-view padding")

print("\n=== Fix 3: BookDetail.vue ===")
bd = f"{base}/components/BookDetail.vue"
replace_in_file(bd, '    <div class="grid sm:grid-cols-3 mb-4 sm:w-10/12">\n      <div />', '    <div class="flex flex-col sm:grid sm:grid-cols-3 mb-4 w-full px-3 sm:px-0 sm:w-10/12">\n      <div class="hidden sm:block" />', "Title grid")
replace_in_file(bd, '        class="flex items-center flex-wrap"', '        class="flex items-center flex-wrap gap-1 mt-2 sm:mt-0"', "Buttons wrapper")
replace_in_file(bd, '          class="btn btn-primary btn-outline mr-2 p-2 uppercase"', '          class="btn btn-primary btn-outline btn-sm sm:btn-md mr-1 sm:mr-2 p-1.5 sm:p-2 uppercase"', "Edit btn")
replace_in_file(bd, '          class="btn btn-error btn-outline mr-2 p-2 uppercase"', '          class="btn btn-error btn-outline btn-sm sm:btn-md mr-1 sm:mr-2 p-1.5 sm:p-2 uppercase"', "Delete btn")
replace_in_file(bd, '          class="btn btn-info btn-outline p-2 uppercase"', '          class="btn btn-info btn-outline btn-sm sm:btn-md p-1.5 sm:p-2 uppercase"', "Event btn")
replace_in_file(bd, '      class="justify-center justify-items-center sm:gap-10 grid grid-cols-1 sm:grid-cols-2 sm:w-10/12 w-full"', '      class="justify-center justify-items-center sm:gap-10 grid grid-cols-1 sm:grid-cols-2 sm:w-10/12 w-full px-4 sm:px-0"', "Cover grid")
replace_in_file(bd, '            class="max-h-96"', '            class="max-h-72 sm:max-h-96 mx-auto"', "Cover image")

print("\n=== Fix 4: Welcome.vue ===")
wv = f"{base}/components/Welcome.vue"
replace_in_file(wv, '      <div class="flex flex-col lg:flex-row gap-6">', '      <div class="flex flex-col lg:flex-row gap-4 sm:gap-6 px-2 sm:px-0">', "Layout gap")
replace_in_file(wv, '          <div class="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3">', '          <div class="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-2 sm:gap-3">', "Reading grid")
with open(wv, 'r') as f:
    c = f.read()
c = c.replace('          <h2 class="text-2xl font-bold pb-4">\n            {{ t(\'home.currently_reading\') }}', '          <h2 class="text-xl sm:text-2xl font-bold pb-2 sm:pb-4">\n            {{ t(\'home.currently_reading\') }}', 1)
c = c.replace('          <h2 class="text-2xl font-bold pb-4">\n            {{ t(\'library_map.rediscover\') }}', '          <h2 class="text-xl sm:text-2xl font-bold pb-2 sm:pb-4">\n            {{ t(\'library_map.rediscover\') }}', 1)
c = c.replace('      <h2 class="text-2xl font-bold">\n        {{ t(\'home.not_reading\') }}', '      <h2 class="text-xl sm:text-2xl font-bold">\n        {{ t(\'home.not_reading\') }}', 1)
c = c.replace('      <h2 class="text-2xl font-bold">\n        {{ t(\'home.recent_events\') }}', '      <h2 class="text-xl sm:text-2xl font-bold">\n        {{ t(\'home.recent_events\') }}', 1)
c = c.replace('      class="text-2xl font-bold py-4 capitalize"', '      class="text-xl sm:text-2xl font-bold py-4 capitalize"', 1)
c = c.replace('      class="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-3"\n    >\n      <div\n        v-for="event in events"', '      class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 sm:gap-3"\n    >\n      <div\n        v-for="event in events"', 1)
c = c.replace('      class="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-3"\n    >\n      <div\n        v-for="review in userReviews"', '      class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-2 sm:gap-3"\n    >\n      <div\n        v-for="review in userReviews"', 1)
with open(wv, 'w') as f:
    f.write(c)
print("  OK: All headings and grids")

print("\n=== Fix 5: BookCard.vue ===")
replace_in_file(f"{base}/components/BookCard.vue",
'    class="card card-sm bg-base-200 border border-base-300 shadow-md"',
'    class="card card-sm bg-base-200 border border-base-300 shadow-md w-full overflow-hidden"',
"Card overflow")

print("\n=== Done! ===")
