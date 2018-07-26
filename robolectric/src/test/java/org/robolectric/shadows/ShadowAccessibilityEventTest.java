package org.robolectric.shadows;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.os.Parcel;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class ShadowAccessibilityEventTest {

  private AccessibilityEvent event;

  @Before
  public void setUp() {
    event = AccessibilityEvent.obtain();
  }

  @Test
  public void shouldRecordParcelables() {
    final Notification notification = new Notification();
    event.setParcelableData(notification);
    AccessibilityEvent anotherEvent = AccessibilityEvent.obtain(event);
    assertThat(anotherEvent.getParcelableData()).isInstanceOf(Notification.class);
    assertThat(anotherEvent.getParcelableData()).isEqualTo(notification);
    anotherEvent.recycle();
  }

  @Test
  public void shouldBeEqualToClonedEvent() {
    event.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
    AccessibilityEvent newEvent = AccessibilityEvent.obtain(event);
    assertThat(event.getEventType()).isEqualTo(newEvent.getEventType());
    assertThat(event.isEnabled()).isEqualTo(newEvent.isEnabled());
    assertThat(event.getContentDescription()).isEqualTo(newEvent.getContentDescription());
    assertThat(event.getPackageName()).isEqualTo(newEvent.getPackageName());
    assertThat(event.getClassName()).isEqualTo(newEvent.getClassName());
    assertThat(event.getParcelableData()).isEqualTo(newEvent.getParcelableData());

    newEvent.recycle();
  }

  @Test
  public void shouldWriteAndReadFromParcelCorrectly() {
    Parcel p = Parcel.obtain();
    event.setContentDescription("test");
    event.writeToParcel(p, 0);
    p.setDataPosition(0);
    AccessibilityEvent anotherEvent = AccessibilityEvent.CREATOR.createFromParcel(p);
    assertThat(anotherEvent.getEventType()).isEqualTo(event.getEventType());
    assertThat(anotherEvent.isEnabled()).isEqualTo(event.isEnabled());
    assertThat(anotherEvent.getContentDescription()).isEqualTo(event.getContentDescription());
    assertThat(anotherEvent.getPackageName()).isEqualTo(event.getPackageName());
    assertThat(anotherEvent.getClassName()).isEqualTo(event.getClassName());
    assertThat(anotherEvent.getParcelableData()).isEqualTo(event.getParcelableData());
    anotherEvent.setContentDescription(null);
    anotherEvent.recycle();
  }

  @Test
  public void shouldHaveCurrentSourceId() {
    TextView rootView = new TextView(RuntimeEnvironment.application);
    event.setSource(rootView);
    assertThat(shadowOf(event).getSourceRoot()).isEqualTo(rootView);
    assertThat(shadowOf(event).getVirtualDescendantId())
        .isEqualTo(ShadowAccessibilityRecord.NO_VIRTUAL_ID);
    event.setSource(rootView, 1);
    assertThat(shadowOf(event).getVirtualDescendantId()).isEqualTo(1);
  }

  @After
  public void tearDown() {
    event.recycle();
  }
}

