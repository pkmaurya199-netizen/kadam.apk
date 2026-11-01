KADAM APK BANANE KE STEPS (bina code ke, free)

1. github.com par free account banao aur login karo.
2. Upar "+" dabao > "New repository". Naam: kadam-app. "Public" chuno. "Create repository" dabao.
3. Is zip ko computer par unzip karo.
4. Naye repository ke page par "uploading an existing file" link dabao.
5. Unzip kiye hue folder ke ANDAR ki saari files aur folders (app, build.gradle, settings.gradle, gradle.properties, README.txt) drag karke daalo. "Commit changes" dabao.
6. ".github" folder drag se na jaye to: "Add file" > "Create new file" dabao. Naam ki jagah ye likho:
   .github/workflows/build.yml
   Aur is zip ke andar .github/workflows/build.yml ka poora text copy karke paste karo. "Commit changes" dabao.
7. Upar "Actions" tab kholo. "Build APK" chuno > "Run workflow" dabao. 3 se 5 minute ruko.
8. Hara tick aane par us run ko kholo. Neeche "Artifacts" mein "Kadam-APK" download karo. Zip kholo, andar app-debug.apk hai.
9. APK ko phone mein bhejo, kholo, aur "Install unknown apps" ki permission do. Install ho jayega.

Nota: ye debug APK hai. Play Store par daalne ke liye alag signed release build chahiye hoga.
