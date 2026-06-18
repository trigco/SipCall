# Ad and Balance Integration Plan

## 1. Balance Fetching and Display
- **Host Filtering**: Only show balance for `sip.amarip.net` or `103.170.231.10`.
- **UI Interaction**:
    - Button in navbar with animated ৳ icon following digits (e.g., `62.85৳`).
    - **Logic**:
        - 1-5 checks: Fetch and show balance for 10s.
        - 6+ checks: Trigger script-based Ad Dialog.
    - **Ad Sync**: Ad displays for 10s and auto-dismisses simultaneously with the balance visibility.

## 2. Audio Record Ad
- **Trigger**: In the audio player/share logic, whenever plays.
- **Action**: Show Ad Dialog as much as the audio playing. when share button clicked show add.

## 3. Codec Selection and Ad
- **Settings UI**: Added "Codecs" section in Settings.
- **Trigger**: Opening/interacting with codec settings triggers the ad if count reached.
- **Action**: Show Ad Dialog for 10s, then auto-dismiss.

## 4. Ad Component (WebView)
- **Content**: Script-based banner (`45b31fc24c18f055ba13d7742fbd8eae`).
- **UI**: Support message "Kindly see ads to support developer", "X" close button.
- **Smoothness**: Non-blocking Dialog, auto-dismiss after 10s.

## Implementation Progress
1. [x] **Update `SipViewModel`**: Added `showAd` state, `triggerAd` logic, and `balanceCheckCount`.
2. [x] **Update Navbar UI**: Fixed ৳ positioning and 10s visibility.
3. [ ] **Auto-Dismiss Logic**: Link ad dismissal to 10s timer.
4. [ ] **Extended Triggers**: Implement codec and recording ad triggers.
